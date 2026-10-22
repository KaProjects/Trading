"""Service API (T0 contract): HTTP + WebSocket, served over a Unix socket by main.py."""

import asyncio
import contextlib
from contextlib import asynccontextmanager
from decimal import Decimal

from collections.abc import Awaitable, Callable

from fastapi import FastAPI, Query, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from trader_service.broker.base import (
    Broker,
    BrokerError,
    InvalidModification,
    NotConnected,
    OrderNotActive,
    UnknownContract,
    UnknownOrder,
)
from trader_service.broker.fake import FakeBroker
from trader_service.control import run_control
from trader_service.events import EventBus
from trader_service.execution import execution_report
from trader_service.models import (
    AccountSummary,
    ConnectionState,
    Contract,
    Fill,
    ModifyOrderRequest,
    Order,
    OrderPreview,
    OrderRequest,
    PlaceOrderRequest,
    Position,
    Price,
    RuleThresholds,
    ActivityNote,
    Side,
    Status,
    Watchlist,
    PlanRecord,
    ExecutionReport,
)
from trader_service.prices.base import PriceFeed, price_symbol
from trader_service.prices.fake import FakePrices
from trader_service.rules import RuleSettings, preview_order
from trader_service.store import Store
from trader_service.vault import VaultLocked

ERROR_STATUS = {
    NotConnected: 409,
    OrderNotActive: 409,
    UnknownOrder: 404,
    UnknownContract: 404,
    InvalidModification: 422,
}


class OrderBlocked(Exception):
    def __init__(self, preview: OrderPreview) -> None:
        self.preview = preview


class SlowRequest(BaseModel):
    seconds: float


class AutoApprove(BaseModel):
    enabled: bool


class SetPrice(BaseModel):
    symbol: str
    price: Decimal


def create_app(broker: Broker, prices: PriceFeed, bus: EventBus, store: Store,
               control: Callable[[], Awaitable[asyncio.StreamReader]] | None = None,
               on_control_eof: Callable[[], None] = lambda: None) -> FastAPI:
    """`control` opens the app's private channel (our stdin) carrying unlock/lock messages (D3.3)."""
    prices.add_listener(lambda price: bus.publish("price", price))

    def update_price_stream() -> None:
        """Real price streams run only while the app is unlocked and the broker session is connected:
        Stop, a lost session or a lock closes the Finnhub WebSocket; login/unlock reopens it."""
        prices.set_active(store.unlocked and broker.status().state == ConnectionState.CONNECTED)

    async def follow_status() -> None:
        queue = bus.subscribe()
        try:
            while True:
                if (await queue.get()).type == "status":
                    update_price_stream()
        finally:
            bus.unsubscribe(queue)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        await prices.start()
        await broker.start()
        await store.start()
        tasks = [asyncio.create_task(follow_status())]
        if control is not None:
            tasks.append(asyncio.create_task(
                run_control(await control(), store, on_control_eof, prices, on_change=update_price_stream)))
        update_price_stream()
        yield
        for task in tasks:
            task.cancel()
        for task in tasks:
            with contextlib.suppress(asyncio.CancelledError):
                await task
        await store.stop()
        await broker.stop()
        await prices.stop()

    app = FastAPI(title="Trader Terminal service", lifespan=lifespan)
    app.state.response_delay = 0.0

    @app.middleware("http")
    async def slow_responses(request: Request, call_next):
        if app.state.response_delay:
            await asyncio.sleep(app.state.response_delay)
        return await call_next(request)

    @app.exception_handler(BrokerError)
    async def broker_error(_: Request, exc: BrokerError) -> JSONResponse:
        return JSONResponse(status_code=ERROR_STATUS.get(type(exc), 400),
                            content={"error": exc.code, "message": str(exc)})

    @app.exception_handler(VaultLocked)
    async def vault_locked(_: Request, exc: VaultLocked) -> JSONResponse:
        return JSONResponse(status_code=423, content={"error": "locked", "message": "Unlock with Touch ID first"})

    @app.exception_handler(OrderBlocked)
    async def order_blocked(_: Request, exc: OrderBlocked) -> JSONResponse:
        return JSONResponse(status_code=422, content={
            "error": exc.preview.block.code, "message": exc.preview.block.message,
            "preview": exc.preview.model_dump(mode="json")})

    def preview(request: OrderRequest, exclude_order_id: int | None = None) -> OrderPreview:
        contract = broker.contract(request.conid)
        account = broker.account_summary()
        position = next((p.quantity for p in broker.positions() if p.contract.conid == contract.conid), Decimal(0))
        pending_sells = sum((o.remaining for o in broker.open_orders()
                             if o.contract.conid == contract.conid and o.side == Side.SELL
                             and o.order_id != exclude_order_id), Decimal(0))
        reserved = Decimal(0)
        if exclude_order_id is not None:
            current = broker.order(exclude_order_id)
            if current.side == Side.BUY and current.status.is_active:
                reserved = current.remaining * current.limit_price * broker.fx_rate(current.contract.currency)
        return preview_order(request, contract, account, broker.fx_rate(contract.currency), position,
                             pending_sells, prices.last(price_symbol(contract)),
                             RuleSettings.from_thresholds(store.rule_thresholds()),
                             reserved_by_this_order=reserved)

    # --- connection ------------------------------------------------------------------------------

    @app.get("/status")
    async def status() -> Status:
        return broker.status()

    @app.post("/login")
    async def login() -> Status:
        return await broker.login()

    @app.post("/logout")
    async def logout() -> Status:
        return await broker.logout()

    # --- account -------------------------------------------------------------------------------------

    @app.get("/account")
    async def account() -> AccountSummary:
        return broker.account_summary()

    @app.get("/positions")
    async def positions() -> list[Position]:
        return broker.positions()

    @app.get("/orders")
    async def orders() -> list[Order]:
        return broker.open_orders()

    @app.get("/fills")
    async def fills(days: int = Query(7, ge=1, le=7)) -> list[Fill]:
        return broker.fills(days)

    @app.get("/contracts")
    async def contracts(symbol: str = Query(min_length=1, max_length=20)) -> list[Contract]:
        return broker.lookup_contract(symbol)

    # --- persisted data (needs the data key, D3.3) -------------------------------------------------------

    @app.get("/vault")
    async def vault() -> dict:
        return {"unlocked": store.unlocked}

    @app.get("/snapshot")
    async def snapshot() -> JSONResponse:
        """Last saved account state, shown greyed out while not connected."""
        return JSONResponse(store.snapshot())

    @app.get("/history")
    async def history(days: int = Query(30, ge=1, le=3660)) -> list[dict]:
        """Own trade history (every fill ever seen), beyond IBKR's 7-day window."""
        return store.history(days)

    @app.get("/settings/rules")
    async def get_rules() -> RuleThresholds:
        return store.rule_thresholds()

    @app.put("/settings/rules")
    async def put_rules(thresholds: RuleThresholds) -> RuleThresholds:
        store.save_rule_thresholds(thresholds)  # 423 while locked
        return thresholds

    @app.post("/activity", status_code=204)
    async def activity(note: ActivityNote) -> None:
        store.record(note.kind, **note.detail)

    # --- orders --------------------------------------------------------------------------------------

    @app.post("/plans", status_code=204)
    async def save_plan(plan: PlanRecord) -> None:
        """T10: record an executed plan at Place (423 while locked)."""
        store.save_plan(plan.model_dump(mode="json"))

    @app.get("/execution")
    async def execution() -> ExecutionReport:
        """Executed plans with FIFO lots, live value and profit, plus the "Outside plans" group."""
        plans = [PlanRecord(**p) for p in store.plans()]
        fills = store.all_fills()
        positions = broker.positions()
        contracts: dict[int, tuple[str, str]] = {}
        for fill in fills:
            c = fill["contract"]
            contracts[int(c["conid"])] = (c["symbol"], c["currency"])
        for plan in plans:
            for r in plan.rows:
                contracts[r.conid] = (r.symbol, r.currency)
        for p in positions:
            contracts[p.contract.conid] = (p.contract.symbol, p.contract.currency)

        def price_of(conid: int) -> Decimal | None:
            try:
                price = prices.last(price_symbol(broker.contract(conid)))
            except BrokerError:
                return None
            return price.last if price else None

        return execution_report(plans, fills, positions, broker.account_summary().base_currency, price_of,
                                broker.fx_rate, contracts)

    @app.put("/watchlist")
    async def watchlist(request: Watchlist) -> Watchlist:
        """Planner rows (T8): their prices stream like positions. Not persisted (D8.2)."""
        broker.set_watchlist(set(request.conids))
        return request

    @app.get("/prices")
    async def all_prices() -> list[Price]:
        """Latest price per streamed symbol (positions + open orders, D4.4)."""
        return prices.snapshot()

    @app.get("/quote")
    async def quote(conid: int) -> Price | None:
        """Last price for a contract (fake prices now, Finnhub with T4). Used to pre-fill the limit price."""
        return prices.last(price_symbol(broker.contract(conid)))

    @app.post("/orders/preview")
    async def order_preview(request: OrderRequest, modify_order_id: int | None = None) -> OrderPreview:
        """`modify_order_id`: check a modification — the order's own reservations don't count against it."""
        return preview(request, exclude_order_id=modify_order_id)

    @app.post("/orders")
    async def place_order(request: PlaceOrderRequest) -> Order:
        existing = broker.order_by_ref(request.order_ref)
        if existing is not None:
            return existing
        checked = preview(request)
        if checked.block:
            store.record("order_blocked", order_ref=request.order_ref, symbol=checked.contract.symbol,
                         side=request.side, quantity=str(request.quantity), reason=checked.block.code)
            raise OrderBlocked(checked)
        return await broker.place_order(request)

    @app.patch("/orders/{order_id}")
    async def modify_order(order_id: int, request: ModifyOrderRequest) -> Order:
        current = broker.order(order_id)
        merged = OrderRequest(conid=current.contract.conid, side=current.side,
                              quantity=request.quantity if request.quantity is not None else current.quantity,
                              limit_price=request.limit_price if request.limit_price is not None
                              else current.limit_price, tif=current.tif, outside_rth=current.outside_rth)
        checked = preview(merged, exclude_order_id=order_id)
        if checked.block:
            raise OrderBlocked(checked)
        return await broker.modify_order(order_id, request)

    @app.delete("/orders/{order_id}")
    async def cancel_order(order_id: int) -> Order:
        return await broker.cancel_order(order_id)

    @app.delete("/orders")
    async def cancel_all() -> list[Order]:
        return await broker.cancel_all()

    # --- events --------------------------------------------------------------------------------------

    @app.websocket("/events")
    async def events(ws: WebSocket) -> None:
        await ws.accept()
        queue = bus.subscribe()
        try:
            await ws.send_json({"type": "status", "data": broker.status().model_dump(mode="json")})

            async def send() -> None:
                while True:
                    event = await queue.get()
                    await ws.send_json(event.model_dump(mode="json"))

            async def receive() -> None:
                while True:
                    await ws.receive_text()  # clients send nothing; this only detects disconnects

            tasks = {asyncio.create_task(send()), asyncio.create_task(receive())}
            done, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
            for task in pending:
                task.cancel()
            for task in tasks:  # retrieve every outcome so a disconnect is never logged as an unhandled error
                with contextlib.suppress(asyncio.CancelledError, WebSocketDisconnect):
                    await task
        except WebSocketDisconnect:
            pass
        finally:
            bus.unsubscribe(queue)

    # --- developer menu (fake environment only, D0.3) -------------------------------------------------

    if isinstance(broker, FakeBroker) and isinstance(prices, FakePrices):
        fake, fake_prices = broker, prices

        @app.post("/dev/2fa/approve", status_code=204)
        async def dev_approve() -> None:
            fake.approve_2fa()

        @app.get("/dev/2fa/auto")
        async def dev_get_auto_approve() -> AutoApprove:
            return AutoApprove(enabled=fake.auto_approve)

        @app.post("/dev/2fa/auto", status_code=204)
        async def dev_set_auto_approve(request: AutoApprove) -> None:
            fake.set_auto_approve(request.enabled)

        @app.post("/dev/2fa/fail", status_code=204)
        async def dev_fail() -> None:
            fake.fail_2fa()

        @app.post("/dev/drop-connection", status_code=204)
        async def dev_drop() -> None:
            fake.drop_connection()

        @app.post("/dev/require-login", status_code=204)
        async def dev_require_login() -> None:
            fake.require_login()

        @app.post("/dev/reset", status_code=204)
        async def dev_reset() -> None:
            fake.reset()

        @app.post("/dev/slow", status_code=204)
        async def dev_slow(request: SlowRequest) -> None:
            app.state.response_delay = max(0.0, min(request.seconds, 30.0))

        @app.post("/dev/price", status_code=204)
        async def dev_price(request: SetPrice) -> None:
            fake_prices.set_price(request.symbol, request.price)

    return app
