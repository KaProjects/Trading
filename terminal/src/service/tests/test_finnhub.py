"""FinnhubPrices against a local stand-in for Finnhub (WebSocket server + mocked REST)."""

import asyncio
import json
from decimal import Decimal

import httpx
import pytest
from websockets.asyncio.server import serve

from trader_service.prices.finnhub import FinnhubPrices

KEY = "test-key-123"


class FakeFinnhub:
    def __init__(self) -> None:
        self.tokens: list[str] = []
        self.subscribed: list[str] = []
        self.unsubscribed: list[str] = []
        self.clients = set()
        self.rest_calls: list[tuple[str, str | None]] = []

    async def handler(self, socket) -> None:
        self.tokens.append(socket.request.path.split("token=")[-1])
        self.clients.add(socket)
        try:
            async for raw in socket:
                message = json.loads(raw)
                (self.subscribed if message["type"] == "subscribe" else self.unsubscribed).append(message["symbol"])
        finally:
            self.clients.discard(socket)

    async def trade(self, *trades: tuple[str, float, int]) -> None:
        data = [{"s": s, "p": p, "t": t, "v": 1} for s, p, t in trades]
        for socket in list(self.clients):
            await socket.send(json.dumps({"type": "trade", "data": data}))

    def rest(self, request: httpx.Request) -> httpx.Response:
        symbol = request.url.params["symbol"]
        self.rest_calls.append((symbol, request.headers.get("X-Finnhub-Token")))
        return httpx.Response(200, json={"c": 230.0, "pc": 225.0, "t": 1_792_000_000})


@pytest.fixture
async def finnhub():
    fake = FakeFinnhub()
    async with serve(fake.handler, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        feed = FinnhubPrices(ws_url=f"ws://127.0.0.1:{port}", rest_url="https://finnhub.test/api/v1",
                             http=httpx.AsyncClient(transport=httpx.MockTransport(fake.rest)), max_symbols=3)
        await feed.start()
        yield feed, fake
        await feed.stop()


async def until(condition, timeout: float = 2.0) -> None:
    async with asyncio.timeout(timeout):
        while not condition():
            await asyncio.sleep(0.01)


async def test_waits_for_key_then_streams_trades(finnhub):
    feed, fake = finnhub
    received = []
    feed.add_listener(received.append)
    feed.subscribe({"AAPL", "MSFT"})
    await asyncio.sleep(0.05)
    assert fake.tokens == [] and not feed.connected  # nothing without a key

    feed.set_key(KEY)
    await until(lambda: set(fake.subscribed) == {"AAPL", "MSFT"})
    assert fake.tokens == [KEY]
    await until(lambda: feed.last("AAPL") is not None)  # first price + previous close from REST
    assert feed.last("AAPL").last == Decimal("230.00") and feed.last("AAPL").prev_close == Decimal("225.0")
    assert ("AAPL", KEY) in fake.rest_calls  # key sent as header, not in the URL

    await fake.trade(("AAPL", 231.5, 1_792_000_001_000), ("AAPL", 231.25, 1_792_000_000_500), ("TSLA", 1.0, 1))
    await until(lambda: feed.last("AAPL").last == Decimal("231.50"))  # latest trade wins
    assert feed.last("AAPL").change_pct == Decimal("2.89")
    assert feed.last("TSLA") is None  # not subscribed
    assert {p.symbol for p in feed.snapshot()} == {"AAPL", "MSFT"}


async def test_subscription_changes_and_symbol_limit(finnhub):
    feed, fake = finnhub
    feed.set_key(KEY)
    feed.subscribe({"AAPL"})
    await until(lambda: fake.subscribed == ["AAPL"])
    feed.subscribe({"MSFT", "NVDA", "SAP.DE", "ZZZZ"})  # limit 3 in this test
    await until(lambda: "AAPL" in fake.unsubscribed)
    await until(lambda: len(fake.subscribed) == 4)
    assert set(fake.subscribed[1:]) == {"MSFT", "NVDA", "SAP.DE"}


async def test_reconnects_after_disconnect(finnhub):
    feed, fake = finnhub
    feed.set_key(KEY)
    feed.subscribe({"AAPL"})
    await until(lambda: len(fake.clients) == 1)
    for socket in list(fake.clients):
        await socket.close()
    await until(lambda: len(fake.tokens) == 2, timeout=5)  # back off 1 s, reconnect
    await until(lambda: fake.subscribed.count("AAPL") == 2)
