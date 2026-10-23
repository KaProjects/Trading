"""IBKR adapter (T1): IB Gateway started by IBC, TWS API via ib_async. Same interface as FakeBroker.

Login: credentials (from the app's Keychain, over the control channel) → IBC starts the Gateway and logs in →
IBKR pushes a 2FA request to the phone → once the Gateway's API port answers we are connected. The account must
match the build: paper accounts start with `DU`, live ones don't (T7).
"""

import asyncio
import contextlib
import logging
import math
from datetime import UTC, datetime, timedelta
from decimal import Decimal

import ib_async

from trader_service.broker.base import (
    Broker,
    BrokerError,
    InvalidModification,
    NotConnected,
    OrderNotActive,
    UnknownContract,
    UnknownOrder,
)
from trader_service.events import EventBus
from trader_service.ibc import GATEWAY_PORT, IbcConfig, IbcError, IbcProcess
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
    Side,
    Status,
    TimeInForce,
)
from trader_service.prices.base import PriceFeed, price_symbol

log = logging.getLogger(__name__)

CENT = Decimal("0.01")
CLIENT_ID = 17
RECONNECT_FOR = timedelta(minutes=10)  # covers the Gateway's daily restart

# ib_async order states → ours. PendingCancel is still working at the exchange.
STATUS = {
    "PendingSubmit": OrderStatus.PENDING_SUBMIT, "ApiPending": OrderStatus.PENDING_SUBMIT,
    "PreSubmitted": OrderStatus.SUBMITTED, "Submitted": OrderStatus.SUBMITTED, "ApiUpdate": OrderStatus.SUBMITTED,
    "PendingCancel": OrderStatus.SUBMITTED,
    "Filled": OrderStatus.FILLED,
    "Cancelled": OrderStatus.CANCELLED, "ApiCancelled": OrderStatus.CANCELLED,
    "Inactive": OrderStatus.REJECTED, "ValidationError": OrderStatus.REJECTED,
}


def _now() -> datetime:
    return datetime.now(UTC)


def _dec(value) -> Decimal | None:
    """ib_async uses float/str and UNSET_DOUBLE (huge) or nan for missing values."""
    if value is None or value == "":
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if math.isnan(number) or abs(number) >= 1e300:
        return None
    return Decimal(str(value))


def _reason(trade: ib_async.Trade) -> str | None:
    """Why IBKR rejected or cancelled an order (e.g. its price cap), from the trade log; None for a plain cancel."""
    for entry in reversed(trade.log):
        if not entry.errorCode or not entry.message:
            continue
        text = " ".join(entry.message.replace("<br>", " ").split())
        text = text.removeprefix("Order Canceled - reason:").removeprefix("Cancelled by System:").strip()
        if text:
            return text
    return None


def _fill_time(f: ib_async.Fill) -> datetime:
    """When the execution happened, in UTC. Live executions come from IBKR in UTC without a zone and ib_async
    stamps them with the Gateway's zone (2 h off in Prague); its `Fill.time` — the arrival time for live fills,
    the execution time for fills fetched at connect — is right in both cases."""
    time = f.time or f.execution.time
    return time if time.tzinfo else time.replace(tzinfo=UTC)


class IbkrBroker(Broker):
    def __init__(self, bus: EventBus, prices: PriceFeed, environment: Environment, work_dir,
                 config: IbcConfig | None = None, ib: ib_async.IB | None = None, keep_gateway: bool = False) -> None:
        if environment not in GATEWAY_PORT:
            raise ValueError(f"IbkrBroker is for paper or production, not {environment}")
        self._bus = bus
        self._prices = prices
        self._environment = environment
        self._config = config or IbcConfig()
        self._ibc = IbcProcess(self._config, environment, work_dir)
        # Debug + paper: quitting leaves the Gateway (and its IBKR session) running for the next start.
        self._keep_gateway = keep_gateway
        self._ib = ib or ib_async.IB()
        self._status = Status(state=ConnectionState.DISCONNECTED, environment=environment, since=_now())
        self._credentials: tuple[str, str] | None = None
        self._tasks: set[asyncio.Task] = set()
        self._login_task: asyncio.Task | None = None
        self._stopping = False
        self._account_id: str | None = None
        self._contracts: dict[int, Contract] = {}
        self._watchlist: set[int] = set()
        self._account_publish: asyncio.TimerHandle | None = None
        ib_ = self._ib
        ib_.disconnectedEvent += self._on_disconnected
        ib_.orderStatusEvent += self._on_order
        ib_.openOrderEvent += self._on_order
        ib_.commissionReportEvent += self._on_commission  # after execDetails: the fill is complete then
        ib_.positionEvent += self._on_position
        ib_.accountValueEvent += self._on_account_value
        ib_.errorEvent += self._on_error

    # --- lifecycle -------------------------------------------------------------------------------

    async def start(self) -> None:
        # The Gateway starts at Log in — unless an earlier run left one running: connect to it (no new 2FA).
        if self._ibc.running:
            self._set_status(ConnectionState.LOGGING_IN, "Connecting to the running IB Gateway")
            self._spawn(self._attach())

    async def _attach(self) -> None:
        for _ in range(15):  # the Gateway may be in its daily restart
            if await self._try_connect():
                return
            if not self._ibc.running:
                break
            await asyncio.sleep(2)
        if self._status.state == ConnectionState.LOGGING_IN:
            self._set_status(ConnectionState.DISCONNECTED)

    async def stop(self) -> None:
        self._stopping = True
        for task in list(self._tasks):
            task.cancel()
        for task in list(self._tasks):
            with contextlib.suppress(asyncio.CancelledError):
                await task
        self._ib.disconnect()
        if self._keep_gateway:
            self._ibc.detach()
        else:
            await self._ibc.stop()
        self._credentials = None

    def _spawn(self, coro) -> asyncio.Task:
        task = asyncio.create_task(coro)
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        return task

    # --- connection --------------------------------------------------------------------------------

    def set_credentials(self, username: str, password: str) -> None:
        """From the app (control channel), right before Log in. Kept only until the Gateway has them."""
        self._credentials = (username, password)

    def status(self) -> Status:
        return self._status

    def _set_status(self, state: ConnectionState, message: str | None = None) -> None:
        self._status = Status(state=state, environment=self._environment,
                              account_id=self._account_id if state == ConnectionState.CONNECTED else None,
                              since=_now(), message=message)
        self._bus.publish("status", self._status)
        self._update_price_subscriptions()

    async def login(self) -> Status:
        if self._status.state in (ConnectionState.CONNECTED, ConnectionState.LOGGING_IN):
            return self._status
        self._stopping = False
        if await self._try_connect():  # a Gateway from before is still logged in
            return self._status
        for _ in range(20):  # the credentials come over stdin, the request over the socket: allow 1 s of skew
            if self._credentials is not None:
                break
            await asyncio.sleep(0.05)
        if self._credentials is None:
            self._set_status(ConnectionState.LOGIN_FAILED, "No IBKR credentials — add them in Settings")
            return self._status
        username, password = self._credentials
        self._credentials = None
        try:
            await self._ibc.stop()
            await self._ibc.start(username, password)
        except (IbcError, OSError) as error:
            self._set_status(ConnectionState.LOGIN_FAILED, str(error))
            return self._status
        self._set_status(ConnectionState.LOGGING_IN, "Approve on your phone")
        self._login_task = self._spawn(self._await_login())
        return self._status

    async def _await_login(self) -> None:
        deadline = asyncio.get_running_loop().time() + self._config.login_timeout
        try:
            while asyncio.get_running_loop().time() < deadline:
                if not self._ibc.running:
                    self._set_status(ConnectionState.LOGIN_FAILED, "2FA not approved in time")
                    return
                if await self._try_connect():
                    return
                await asyncio.sleep(2)
            await self._ibc.stop()
            self._set_status(ConnectionState.LOGIN_FAILED, "2FA not approved in time")
        finally:
            self._ibc.delete_ini()  # logged in (or failed): the credentials file goes either way

    async def _try_connect(self) -> bool:
        """Connect to the Gateway's API if it answers (only after a successful login)."""
        try:
            await self._ib.connectAsync("127.0.0.1", GATEWAY_PORT[self._environment], clientId=CLIENT_ID,
                                        timeout=5)
        except (ConnectionRefusedError, OSError, TimeoutError, asyncio.TimeoutError):
            self._ib.disconnect()
            return False
        accounts = self._ib.managedAccounts()
        account = accounts[0] if accounts else None
        paper = self._environment == Environment.PAPER
        if account is None or account.startswith("DU") != paper:
            self._ib.disconnect()
            await self._ibc.stop()
            kind = "paper (DU…)" if paper else "live"
            self._set_status(ConnectionState.LOGIN_FAILED, f"Not a {kind} account — check the credentials")
            return True
        self._account_id = account
        await self._load_contract_names()
        self._ibc.delete_ini()
        self._set_status(ConnectionState.CONNECTED)
        self._bus.publish("account", self._summary())
        return True

    async def logout(self) -> Status:
        self._stopping = True
        if self._login_task:
            self._login_task.cancel()
        self._ib.disconnect()
        await self._ibc.stop()
        self._stopping = False
        self._set_status(ConnectionState.DISCONNECTED)
        return self._status

    def _on_disconnected(self) -> None:
        if self._stopping or self._status.state != ConnectionState.CONNECTED:
            return
        self._set_status(ConnectionState.RECONNECTING, "Connection to IB Gateway lost")
        self._spawn(self._reconnect())

    async def _reconnect(self) -> None:
        deadline = _now() + RECONNECT_FOR
        while _now() < deadline and not self._stopping:
            if not self._ibc.running:
                break
            await asyncio.sleep(5)
            if await self._try_connect():
                return
        if not self._stopping:
            await self._ibc.stop()
            self._set_status(ConnectionState.LOGIN_REQUIRED, "IB Gateway session ended — log in again")

    def _require_connected(self) -> None:
        if self._status.state != ConnectionState.CONNECTED or not self._ib.isConnected():
            raise NotConnected("Not connected to IBKR")

    def _on_error(self, req_id, code, message, contract) -> None:
        # Connectivity notices (2104/2106/2158 farm OK, 1100/1102 lost/restored) are informational here.
        log.info("IBKR %s: %s", code, message)

    # --- contracts ---------------------------------------------------------------------------------

    def _contract(self, c: ib_async.Contract, name: str | None = None) -> Contract:
        """Merged with what we know: fills arrive with little more than the conid, and an empty symbol must never
        replace a known one (it emptied the Fills table and broke the price subscriptions)."""
        known = self._contracts.get(c.conId)
        symbol = c.symbol or (known.symbol if known else "")
        contract = Contract(
            conid=c.conId, symbol=symbol or str(c.conId),
            name=name or (known.name if known else "") or symbol or str(c.conId),
            sec_type=c.secType or (known.sec_type if known else "STK"), exchange="SMART",
            primary_exchange=c.primaryExchange or (known.primary_exchange if known else "") or c.exchange,
            currency=c.currency or (known.currency if known else "USD"))
        if symbol or known is None:
            self._contracts[c.conId] = contract
        if not symbol:
            self._spawn(self._fetch_contract(c.conId))
        return contract

    async def _fetch_contract(self, conid: int) -> None:
        with contextlib.suppress(Exception):
            details = await self._ib.reqContractDetailsAsync(ib_async.Contract(conId=conid))
            if details:
                self._contract(details[0].contract, details[0].longName)

    async def _load_contract_names(self) -> None:
        """Positions and orders arrive without the company name / primary exchange: fetch them once."""
        wanted = {p.contract.conId: p.contract for p in self._ib.positions()}
        wanted |= {t.contract.conId: t.contract for t in self._ib.trades()}
        wanted |= {f.contract.conId: f.contract for f in self._ib.fills()}
        for conid, contract in wanted.items():
            if conid in self._contracts and self._contracts[conid].name != self._contracts[conid].symbol:
                continue
            with contextlib.suppress(Exception):
                details = await self._ib.reqContractDetailsAsync(ib_async.Contract(conId=conid))
                if details:
                    self._contract(details[0].contract, details[0].longName)
                    continue
            self._contract(contract)

    async def lookup_contract(self, symbol: str) -> list[Contract]:
        self._require_connected()
        details = await self._ib.reqContractDetailsAsync(ib_async.Stock(symbol.strip().upper(), "SMART"))
        return [self._contract(d.contract, d.longName) for d in details if d.contract.secType == "STK"]

    def contract(self, conid: int) -> Contract:
        try:
            return self._contracts[conid]
        except KeyError:
            raise UnknownContract(f"Unknown contract {conid}") from None

    # --- account -----------------------------------------------------------------------------------

    def _values(self) -> list[ib_async.AccountValue]:
        return [v for v in self._ib.accountValues(self._account_id or "") if not v.modelCode]

    def _base_currency(self) -> str:
        for value in self._values():
            if value.tag == "NetLiquidation" and value.currency not in ("", "BASE"):
                return value.currency
        return "USD"

    def fx_rate(self, currency: str) -> Decimal:
        base = self._base_currency()
        if currency == base:
            return Decimal(1)
        for value in self._values():
            if value.tag == "ExchangeRate" and value.currency == currency:
                rate = _dec(value.value)
                if rate:
                    return rate
        raise BrokerError(f"No exchange rate for {currency}")

    def account_summary(self) -> AccountSummary:
        self._require_connected()
        return self._summary()

    def _summary(self) -> AccountSummary:
        base = self._base_currency()
        tags: dict[str, Decimal] = {}
        cash: dict[str, Decimal] = {}
        fx: dict[str, Decimal] = {base: Decimal(1)}
        for value in self._values():
            amount = _dec(value.value)
            if amount is None:
                continue
            if value.currency == base and value.tag in ("NetLiquidation", "TotalCashValue", "AvailableFunds",
                                                        "BuyingPower"):
                tags[value.tag] = amount
            elif value.tag == "CashBalance" and value.currency not in ("", "BASE") and amount != 0:
                cash[value.currency] = amount
            elif value.tag == "ExchangeRate" and value.currency not in ("", "BASE"):
                fx[value.currency] = amount

        def q(tag: str) -> Decimal:
            return tags.get(tag, Decimal(0)).quantize(CENT)

        return AccountSummary(
            account_id=self._account_id or "", base_currency=base, net_liquidation=q("NetLiquidation"),
            total_cash=q("TotalCashValue"), available_funds=q("AvailableFunds"), buying_power=q("BuyingPower"),
            cash=[CashBalance(currency=c, amount=a.quantize(CENT)) for c, a in sorted(cash.items())],
            fx_to_base=fx)

    def _on_account_value(self, _value) -> None:
        # Values arrive in bursts (dozens per update): publish once per burst.
        if self._status.state != ConnectionState.CONNECTED:
            return
        if self._account_publish:
            self._account_publish.cancel()
        self._account_publish = asyncio.get_running_loop().call_later(
            0.5, lambda: self._bus.publish("account", self._summary()))

    def positions(self) -> list[Position]:
        self._require_connected()
        return [self._position(p) for p in self._ib.positions(self._account_id or "") if p.position != 0]

    def _position(self, p: ib_async.Position) -> Position:
        quantity = Decimal(str(p.position))
        avg_cost = Decimal(str(p.avgCost)).quantize(CENT)
        return Position(contract=self._contract(p.contract), quantity=quantity, avg_cost=avg_cost,
                        cost_basis=(quantity * avg_cost).quantize(CENT))

    def _on_position(self, p: ib_async.Position) -> None:
        if self._status.state == ConnectionState.CONNECTED:
            self._bus.publish("position", self._position(p))
            self._update_price_subscriptions()

    # --- orders ------------------------------------------------------------------------------------

    def _order(self, trade: ib_async.Trade) -> Order:
        o, s = trade.order, trade.orderStatus
        status = STATUS.get(s.status, OrderStatus.SUBMITTED)
        reason = _reason(trade) if status in (OrderStatus.REJECTED, OrderStatus.CANCELLED) else None
        if status == OrderStatus.REJECTED and reason is None:
            reason = "Rejected by IBKR"
        filled = Decimal(str(s.filled or 0))
        quantity = Decimal(str(o.totalQuantity))
        times = [e.time for e in trade.log if e.time] or [_now()]
        return Order(
            order_id=o.orderId if o.orderId > 0 else o.permId, order_ref=o.orderRef or "",
            contract=self._contract(trade.contract), side=Side(o.action), quantity=quantity,
            limit_price=_dec(o.lmtPrice) or Decimal(0), tif=TimeInForce.GTC if o.tif == "GTC" else TimeInForce.DAY,
            outside_rth=bool(o.outsideRth), status=status, filled=filled,
            remaining=_dec(s.remaining) if _dec(s.remaining) is not None else quantity - filled,
            avg_fill_price=_dec(s.avgFillPrice) if filled else None, reject_reason=reason,
            created_at=min(times), updated_at=max(times))

    def _trade(self, order_id: int) -> ib_async.Trade:
        for trade in self._ib.trades():
            if order_id in (trade.order.orderId, trade.order.permId):
                return trade
        raise UnknownOrder(f"Unknown order {order_id}")

    def open_orders(self) -> list[Order]:
        self._require_connected()
        orders = [self._order(t) for t in self._ib.openTrades()]
        return [o for o in orders if o.status.is_active]

    def order(self, order_id: int) -> Order:
        self._require_connected()
        return self._order(self._trade(order_id))

    def order_by_ref(self, order_ref: str) -> Order | None:
        self._require_connected()
        trade = next((t for t in self._ib.trades() if t.order.orderRef == order_ref), None)
        return self._order(trade) if trade else None

    def _on_order(self, trade: ib_async.Trade) -> None:
        if self._status.state == ConnectionState.CONNECTED:
            self._bus.publish("order", self._order(trade))
            self._update_price_subscriptions()

    async def place_order(self, request: PlaceOrderRequest) -> Order:
        self._require_connected()
        if (existing := self.order_by_ref(request.order_ref)) is not None:
            return existing  # same ref twice -> no second order
        self.contract(request.conid)  # raises UnknownContract
        contract = ib_async.Contract(conId=request.conid, exchange="SMART")
        order = ib_async.LimitOrder(request.side.value, float(request.quantity), float(request.limit_price),
                                    tif=request.tif.value, outsideRth=request.outside_rth,
                                    orderRef=request.order_ref, account=self._account_id or "")
        trade = self._ib.placeOrder(contract, order)
        return self._order(trade)

    async def modify_order(self, order_id: int, request: ModifyOrderRequest) -> Order:
        self._require_connected()
        trade = self._trade(order_id)
        current = self._order(trade)
        if not current.status.is_active or trade.order.orderId <= 0:
            raise OrderNotActive(f"Order {order_id} can't be modified here")
        if request.quantity is not None:
            if request.quantity <= current.filled:
                raise InvalidModification("Quantity must stay above what is already filled")
            trade.order.totalQuantity = float(request.quantity)
        if request.limit_price is not None:
            trade.order.lmtPrice = float(request.limit_price)
        return self._order(self._ib.placeOrder(trade.contract, trade.order))

    async def cancel_order(self, order_id: int) -> Order:
        self._require_connected()
        trade = self._trade(order_id)
        if not self._order(trade).status.is_active:
            raise OrderNotActive(f"Order {order_id} is not active")
        self._ib.cancelOrder(trade.order)
        return self._order(trade)

    async def cancel_all(self) -> list[Order]:
        self._require_connected()
        active = self.open_orders()
        self._ib.reqGlobalCancel()
        return active

    # --- fills -------------------------------------------------------------------------------------

    def _fill(self, f: ib_async.Fill) -> Fill:
        e, report = f.execution, f.commissionReport
        commission = _dec(report.commission) if report else None
        return Fill(
            exec_id=e.execId, order_id=e.orderId or None, order_ref=e.orderRef or None,
            contract=self._contract(f.contract), side=Side.BUY if e.side == "BOT" else Side.SELL,
            quantity=Decimal(str(e.shares)), price=Decimal(str(e.price)),
            commission=commission or Decimal(0), commission_currency=(report.currency if report else "") or "USD",
            time=_fill_time(f))

    def fills(self, days: int) -> list[Fill]:
        self._require_connected()
        since = _now() - timedelta(days=days)
        fills = [self._fill(f) for f in self._ib.fills()]
        return sorted((f for f in fills if f.time >= since), key=lambda f: f.time, reverse=True)

    def _on_commission(self, trade, fill, report) -> None:
        if self._status.state == ConnectionState.CONNECTED:
            self._bus.publish("fill", self._fill(fill))

    # --- prices ------------------------------------------------------------------------------------

    def set_watchlist(self, conids: set[int]) -> None:
        for conid in conids:
            self.contract(conid)  # raises UnknownContract
        self._watchlist = set(conids)
        self._update_price_subscriptions()

    def _update_price_subscriptions(self) -> None:
        """D4.4: stream symbols of positions + open orders (+ planner watchlist, T8)."""
        conids = set(self._watchlist)
        if self._status.state == ConnectionState.CONNECTED and self._ib.isConnected():
            conids |= {p.contract.conId for p in self._ib.positions() if p.position != 0}
            conids |= {t.contract.conId for t in self._ib.openTrades()}
        self._prices.subscribe({price_symbol(self._contracts[c]) for c in conids if c in self._contracts})
