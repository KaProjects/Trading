"""FakeBroker: behaves like an IBKR paper account, entirely in memory (D0.1, D0.5).

Login with a simulated 2FA wait, a seeded account, limit orders that fill against FakePrices
(partial fills for large orders), IBKR-like rejections and failure injection for the developer menu.
"""

import asyncio
import contextlib
import dataclasses
import json
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from importlib import resources
from itertools import count

from trader_service.broker.base import (
    Broker,
    InvalidModification,
    NotConnected,
    OrderNotActive,
    UnknownContract,
    UnknownOrder,
)
from trader_service.events import EventBus
from trader_service.models import (
    AccountSummary,
    CashBalance,
    ConnectionState,
    Contract,
    Environment,
    Fill,
    ModifyOrderRequest,
    Order,
    OrderStatus,
    PlaceOrderRequest,
    Position,
    Price,
    Side,
    Status,
    TimeInForce,
)
from trader_service.prices.base import price_symbol
from trader_service.prices.fake import FakePrices

CENT = Decimal("0.01")
REJECT_SYMBOL = "REJECT"


@dataclass
class FakeConfig:
    twofa_delay: float = 1.0  # seconds until the simulated phone approval (auto_approve)
    auto_approve: bool = True
    twofa_timeout: float = 180.0  # D2.4
    submit_delay: float = 0.3  # PendingSubmit -> Submitted
    reconnect_delay: float = 2.0
    partial_threshold: Decimal = Decimal(500)  # orders above this fill in chunks
    partial_chunk: Decimal = Decimal(200)
    commission: Decimal = Decimal("1.00")
    commission_currency: str = "USD"


def load_seed() -> dict:
    return json.loads(resources.files("trader_service.fixtures").joinpath("fake_seed.json").read_text())


def seed_prices(seed: dict) -> dict[str, Decimal]:
    return {price_symbol(_contract(c)): Decimal(c["price"]) for c in seed["contracts"]}


def _contract(raw: dict) -> Contract:
    return Contract(**{k: v for k, v in raw.items() if k != "price"})


def _now() -> datetime:
    return datetime.now(UTC)


class FakeBroker(Broker):
    def __init__(self, bus: EventBus, prices: FakePrices, config: FakeConfig | None = None,
                 seed: dict | None = None) -> None:
        self._bus = bus
        self._prices = prices
        self._config = config or FakeConfig()
        self._seed = seed or load_seed()
        self._tasks: set[asyncio.Task] = set()
        self._login_task: asyncio.Task | None = None
        self._approve = asyncio.Event()
        self._status = Status(state=ConnectionState.DISCONNECTED, environment=Environment.FAKE, since=_now())
        self._prices.add_listener(self._on_price)
        self._load(self._seed)

    # --- lifecycle -------------------------------------------------------------------------------

    async def start(self) -> None:
        self._update_price_subscriptions()

    async def stop(self) -> None:
        for task in list(self._tasks):
            task.cancel()
        for task in list(self._tasks):
            with contextlib.suppress(asyncio.CancelledError):
                await task
        self._tasks.clear()

    def _spawn(self, coro) -> asyncio.Task:
        task = asyncio.create_task(coro)
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        return task

    # --- connection --------------------------------------------------------------------------------

    def status(self) -> Status:
        return self._status

    async def login(self) -> Status:
        if self._status.state in (ConnectionState.CONNECTED, ConnectionState.LOGGING_IN):
            return self._status
        self._approve.clear()
        self._set_status(ConnectionState.LOGGING_IN, message="Approve on your phone")
        self._login_task = self._spawn(self._twofa())
        return self._status

    async def logout(self) -> Status:
        self._cancel_login()
        self._set_status(ConnectionState.DISCONNECTED)
        return self._status

    async def _twofa(self) -> None:
        cfg = self._config
        auto = cfg.auto_approve and cfg.twofa_delay <= cfg.twofa_timeout
        try:
            await asyncio.wait_for(self._approve.wait(), timeout=cfg.twofa_delay if auto else cfg.twofa_timeout)
            approved = True
        except TimeoutError:
            approved = auto
        if self._status.state != ConnectionState.LOGGING_IN:
            return
        if approved:
            self._set_status(ConnectionState.CONNECTED)
        else:
            self._set_status(ConnectionState.LOGIN_FAILED, message="2FA not approved in time")

    def _cancel_login(self) -> None:
        if self._login_task is not None and not self._login_task.done():
            self._login_task.cancel()
        self._login_task = None

    def _set_status(self, state: ConnectionState, message: str | None = None) -> None:
        account_id = self._account_id if state == ConnectionState.CONNECTED else None
        self._status = Status(state=state, environment=Environment.FAKE, account_id=account_id,
                              since=_now(), message=message)
        self._bus.publish("status", self._status)

    def _require_connected(self) -> None:
        if self._status.state != ConnectionState.CONNECTED:
            raise NotConnected(f"Not connected (state: {self._status.state})")

    # --- developer menu (failure injection, D0.3) ---------------------------------------------------

    def set_auto_approve(self, enabled: bool) -> None:
        """Off: login waits for approve_2fa()/fail_2fa() like a real phone push (or the 2FA timeout)."""
        self._config = dataclasses.replace(self._config, auto_approve=enabled)

    @property
    def auto_approve(self) -> bool:
        return self._config.auto_approve

    def approve_2fa(self) -> None:
        self._approve.set()

    def fail_2fa(self) -> None:
        if self._status.state == ConnectionState.LOGGING_IN:
            self._cancel_login()
            self._set_status(ConnectionState.LOGIN_FAILED, message="2FA not approved in time")

    def drop_connection(self) -> None:
        if self._status.state != ConnectionState.CONNECTED:
            return
        self._set_status(ConnectionState.RECONNECTING)
        self._spawn(self._reconnect())

    async def _reconnect(self) -> None:
        await asyncio.sleep(self._config.reconnect_delay)
        if self._status.state == ConnectionState.RECONNECTING:
            self._set_status(ConnectionState.CONNECTED)

    def require_login(self) -> None:
        self._cancel_login()
        self._set_status(ConnectionState.LOGIN_REQUIRED, message="Session ended (daily restart)")

    def reset(self) -> None:
        self._load(self._seed)
        self._update_price_subscriptions()
        self._bus.publish("account", self._summary())

    # --- account data ----------------------------------------------------------------------------------

    def _load(self, seed: dict) -> None:
        self._account_id: str = seed["account_id"]
        self._base: str = seed["base_currency"]
        self._fx = {c: Decimal(r) for c, r in seed["fx_to_base"].items()}
        self._cash = {c: Decimal(a) for c, a in seed["cash"].items()}
        self._contracts = {c["conid"]: _contract(c) for c in seed["contracts"]}
        self._positions: dict[int, tuple[Decimal, Decimal]] = {
            p["conid"]: (Decimal(p["quantity"]), Decimal(p["avg_cost"])) for p in seed["positions"]
        }
        self._orders: dict[int, Order] = {}
        self._refs: dict[str, int] = {}
        self._order_ids = count(1)
        self._exec_ids = count(1)
        self._fills: list[Fill] = []
        now = _now()
        for raw in seed["open_orders"]:
            order = self._new_order(PlaceOrderRequest(
                conid=raw["conid"], side=Side(raw["side"]), quantity=Decimal(raw["quantity"]),
                limit_price=Decimal(raw["limit_price"]), tif=TimeInForce(raw.get("tif", "DAY")),
                order_ref=raw["order_ref"]), OrderStatus.SUBMITTED)
            self._orders[order.order_id] = order
            self._refs[order.order_ref] = order.order_id
        for raw in seed["fills"]:
            self._fills.append(Fill(
                exec_id=self._next_exec_id(), order_id=None, order_ref=None,
                contract=self._contracts[raw["conid"]], side=Side(raw["side"]),
                quantity=Decimal(raw["quantity"]), price=Decimal(raw["price"]),
                commission=self._config.commission, commission_currency=self._config.commission_currency,
                time=now - timedelta(days=raw["days_ago"])))

    def fx_rate(self, currency: str) -> Decimal:
        return self._fx[currency]

    def account_summary(self) -> AccountSummary:
        self._require_connected()
        return self._summary()

    def _summary(self) -> AccountSummary:
        total_cash = sum((a * self._fx[c] for c, a in self._cash.items()), Decimal(0))
        market_value = Decimal(0)
        for conid, (qty, avg_cost) in self._positions.items():
            contract = self._contracts[conid]
            price = self._prices.last(price_symbol(contract))
            market_value += qty * (price.last if price else avg_cost) * self._fx[contract.currency]
        reserved = sum((o.remaining * o.limit_price * self._fx[o.contract.currency]
                        for o in self._orders.values() if o.status.is_active and o.side == Side.BUY),
                       Decimal(0))
        available = total_cash - reserved
        return AccountSummary(
            account_id=self._account_id, base_currency=self._base,
            net_liquidation=(total_cash + market_value).quantize(CENT),
            total_cash=total_cash.quantize(CENT), available_funds=available.quantize(CENT),
            buying_power=available.quantize(CENT),
            cash=[CashBalance(currency=c, amount=a.quantize(CENT)) for c, a in sorted(self._cash.items())])

    def positions(self) -> list[Position]:
        self._require_connected()
        return [self._position(conid) for conid in self._positions]

    def _position(self, conid: int) -> Position:
        qty, avg_cost = self._positions.get(conid, (Decimal(0), Decimal(0)))
        return Position(contract=self._contracts[conid], quantity=qty, avg_cost=avg_cost.quantize(CENT),
                        cost_basis=(qty * avg_cost).quantize(CENT))

    def open_orders(self) -> list[Order]:
        self._require_connected()
        return [o for o in self._orders.values() if o.status.is_active]

    def order(self, order_id: int) -> Order:
        self._require_connected()
        try:
            return self._orders[order_id]
        except KeyError:
            raise UnknownOrder(f"Unknown order {order_id}") from None

    def order_by_ref(self, order_ref: str) -> Order | None:
        self._require_connected()
        order_id = self._refs.get(order_ref)
        return self._orders[order_id] if order_id is not None else None

    def fills(self, days: int) -> list[Fill]:
        self._require_connected()
        since = _now() - timedelta(days=days)
        return sorted((f for f in self._fills if f.time >= since), key=lambda f: f.time, reverse=True)

    def lookup_contract(self, symbol: str) -> list[Contract]:
        self._require_connected()
        wanted = symbol.strip().upper()
        return [c for c in self._contracts.values() if c.symbol == wanted]

    def contract(self, conid: int) -> Contract:
        try:
            return self._contracts[conid]
        except KeyError:
            raise UnknownContract(f"Unknown contract {conid}") from None

    # --- orders ------------------------------------------------------------------------------------

    async def place_order(self, request: PlaceOrderRequest) -> Order:
        self._require_connected()
        if request.order_ref in self._refs:  # same ref twice -> no second order
            return self._orders[self._refs[request.order_ref]]
        order = self._new_order(request, OrderStatus.PENDING_SUBMIT)  # raises UnknownContract
        reason = self._reject_reason(order)  # before storing, so the order doesn't reserve funds against itself
        self._orders[order.order_id] = order
        self._refs[order.order_ref] = order.order_id
        if reason:
            self._update(order, status=OrderStatus.REJECTED, reject_reason=reason)
            return self._orders[order.order_id]
        self._bus.publish("order", order)
        self._spawn(self._submit(order.order_id))
        self._update_price_subscriptions()
        return order

    def _reject_reason(self, order: Order) -> str | None:
        if order.quantity <= 0:
            return "Quantity must be positive"
        if order.limit_price <= 0:
            return "Limit price must be positive"
        if order.contract.symbol == REJECT_SYMBOL:
            return "Order rejected by exchange (test contract)"
        if order.side == Side.BUY:
            cost = order.quantity * order.limit_price * self._fx[order.contract.currency]
            if cost > self._summary().available_funds:
                return "Insufficient funds"
        return None

    async def _submit(self, order_id: int) -> None:
        await asyncio.sleep(self._config.submit_delay)
        order = self._orders[order_id]
        if order.status != OrderStatus.PENDING_SUBMIT:
            return
        self._update(order, status=OrderStatus.SUBMITTED)
        self._try_match(order_id)

    async def modify_order(self, order_id: int, request: ModifyOrderRequest) -> Order:
        order = self.order(order_id)
        if not order.status.is_active:
            raise OrderNotActive(f"Order {order_id} is {order.status}")
        quantity = request.quantity if request.quantity is not None else order.quantity
        limit = request.limit_price if request.limit_price is not None else order.limit_price
        if quantity <= order.filled:
            raise InvalidModification("Quantity must be above the already filled quantity")
        if limit <= 0:
            raise InvalidModification("Limit price must be positive")
        self._update(order, quantity=quantity, remaining=quantity - order.filled, limit_price=limit)
        self._try_match(order_id)
        return self._orders[order_id]

    async def cancel_order(self, order_id: int) -> Order:
        order = self.order(order_id)
        if not order.status.is_active:
            raise OrderNotActive(f"Order {order_id} is {order.status}")
        self._update(order, status=OrderStatus.CANCELLED)
        self._update_price_subscriptions()
        return self._orders[order_id]

    async def cancel_all(self) -> list[Order]:
        return [await self.cancel_order(o.order_id) for o in self.open_orders()]

    def _new_order(self, request: PlaceOrderRequest, status: OrderStatus) -> Order:
        now = _now()
        return Order(order_id=next(self._order_ids), order_ref=request.order_ref,
                     contract=self.contract(request.conid), side=request.side, quantity=request.quantity,
                     limit_price=request.limit_price, tif=request.tif, outside_rth=request.outside_rth,
                     status=status, remaining=request.quantity, created_at=now, updated_at=now)

    def _update(self, order: Order, **changes) -> Order:
        updated = order.model_copy(update={**changes, "updated_at": _now()})
        self._orders[order.order_id] = updated
        self._bus.publish("order", updated)
        return updated

    # --- matching ------------------------------------------------------------------------------------

    def _on_price(self, price: Price) -> None:
        for order in list(self._orders.values()):
            if order.status == OrderStatus.SUBMITTED and price_symbol(order.contract) == price.symbol:
                self._try_match(order.order_id)

    def _try_match(self, order_id: int) -> None:
        order = self._orders[order_id]
        if order.status != OrderStatus.SUBMITTED:
            return
        price = self._prices.last(price_symbol(order.contract))
        if price is None:
            return
        crosses = price.last <= order.limit_price if order.side == Side.BUY else price.last >= order.limit_price
        if not crosses:
            return
        quantity = order.remaining
        if order.quantity > self._config.partial_threshold:
            quantity = min(order.remaining, self._config.partial_chunk)
        self._fill(order, quantity, price.last)

    def _fill(self, order: Order, quantity: Decimal, price: Decimal) -> None:
        contract = order.contract
        sign = 1 if order.side == Side.BUY else -1
        self._cash[contract.currency] = self._cash.get(contract.currency, Decimal(0)) - sign * quantity * price
        cc = self._config.commission_currency
        self._cash[cc] = self._cash.get(cc, Decimal(0)) - self._config.commission

        qty, avg_cost = self._positions.get(contract.conid, (Decimal(0), Decimal(0)))
        if order.side == Side.BUY:
            new_qty = qty + quantity
            avg_cost = (qty * avg_cost + quantity * price) / new_qty
        else:
            new_qty = qty - quantity
        if new_qty == 0:
            self._positions.pop(contract.conid, None)
        else:
            self._positions[contract.conid] = (new_qty, avg_cost)

        fill = Fill(exec_id=self._next_exec_id(), order_id=order.order_id, order_ref=order.order_ref,
                    contract=contract, side=order.side, quantity=quantity, price=price,
                    commission=self._config.commission, commission_currency=cc, time=_now())
        self._fills.append(fill)

        filled = order.filled + quantity
        avg_fill = ((order.avg_fill_price or Decimal(0)) * order.filled + price * quantity) / filled
        remaining = order.quantity - filled
        self._update(order, filled=filled, remaining=remaining, avg_fill_price=avg_fill.quantize(Decimal("0.0001")),
                     status=OrderStatus.FILLED if remaining == 0 else OrderStatus.SUBMITTED)
        self._bus.publish("fill", fill)
        self._bus.publish("position", self._position(contract.conid))
        self._bus.publish("account", self._summary())
        self._update_price_subscriptions()

    def _next_exec_id(self) -> str:
        return f"FAKE-{next(self._exec_ids):06d}"

    def _update_price_subscriptions(self) -> None:
        """D4.4: stream symbols of positions + open orders."""
        conids = set(self._positions) | {o.contract.conid for o in self._orders.values() if o.status.is_active}
        self._prices.subscribe({price_symbol(self._contracts[c]) for c in conids})
