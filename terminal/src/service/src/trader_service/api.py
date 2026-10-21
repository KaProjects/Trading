"""Service API (T0 contract): HTTP + WebSocket, served over a Unix socket by main.py."""

import asyncio
import contextlib
from contextlib import asynccontextmanager
from decimal import Decimal

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
from trader_service.events import EventBus
from trader_service.models import (
    AccountSummary,
    Contract,
    Fill,
    ModifyOrderRequest,
    Order,
    OrderPreview,
    OrderRequest,
    PlaceOrderRequest,
    Position,
    Side,
    Status,
)
from trader_service.prices.base import PriceFeed, price_symbol
from trader_service.prices.fake import FakePrices
from trader_service.rules import RuleSettings, preview_order

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


class SetPrice(BaseModel):
    symbol: str
    price: Decimal


def create_app(broker: Broker, prices: PriceFeed, bus: EventBus,
               rules: RuleSettings = RuleSettings()) -> FastAPI:
    prices.add_listener(lambda price: bus.publish("price", price))

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        await prices.start()
        await broker.start()
        yield
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
        return preview_order(request, contract, account, broker.fx_rate(contract.currency), position,
                             pending_sells, prices.last(price_symbol(contract)), rules)

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

    # --- orders --------------------------------------------------------------------------------------

    @app.post("/orders/preview")
    async def order_preview(request: OrderRequest) -> OrderPreview:
        return preview(request)

    @app.post("/orders")
    async def place_order(request: PlaceOrderRequest) -> Order:
        existing = broker.order_by_ref(request.order_ref)
        if existing is not None:
            return existing
        checked = preview(request)
        if checked.block:
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
