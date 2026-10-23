"""FinnhubPrices against a local stand-in for Finnhub (WebSocket server + mocked REST)."""

import asyncio
import contextlib
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
        feed.set_active(True)  # the app is unlocked and the broker connected
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


async def test_pause_closes_stream_and_resume_reopens(finnhub):
    feed, fake = finnhub
    feed.set_key(KEY)
    feed.subscribe({"AAPL"})
    await until(lambda: len(fake.clients) == 1)
    feed.set_active(False)  # lock / Stop / session lost
    await until(lambda: len(fake.clients) == 0 and not feed.connected)
    await asyncio.sleep(0.2)
    assert len(fake.tokens) == 1  # no reconnect while paused
    assert feed.last("AAPL") is not None  # last price kept (shown as stale)
    feed.set_active(True)
    await until(lambda: len(fake.clients) == 1)
    await until(lambda: fake.subscribed.count("AAPL") == 2)


async def test_inactive_feed_never_connects():
    feed = FinnhubPrices(ws_url="ws://127.0.0.1:9", http=httpx.AsyncClient(transport=httpx.MockTransport(
        lambda r: httpx.Response(500))))
    await feed.start()
    feed.set_key(KEY)
    feed.subscribe({"AAPL"})
    await asyncio.sleep(0.2)
    assert not feed.connected
    await feed.stop()


async def test_quiet_symbols_are_refreshed_from_quotes():
    """No update for `stale_after` → /quote, stalest first; each update restarts the symbol's wait."""
    calls: list[str] = []

    def rest(request: httpx.Request) -> httpx.Response:
        calls.append(request.url.params["symbol"])
        n = len(calls)
        return httpx.Response(200, json={"c": 100 + n, "pc": 100.0, "t": 1_792_000_000 + n})

    feed = FinnhubPrices(ws_url="ws://127.0.0.1:9", rest_url="https://finnhub.test/api/v1",
                         http=httpx.AsyncClient(transport=httpx.MockTransport(rest)),
                         stale_after=0.3, refresh_tick=0.01)
    await feed.start()
    feed.set_key(KEY)
    feed.set_active(True)
    feed.subscribe({"SKHY", "MU"})
    await until(lambda: feed.last("SKHY") is not None and feed.last("MU") is not None)
    first = feed.last("SKHY").last
    assert len(calls) == 2  # first quotes; nothing stale yet
    await until(lambda: {"SKHY", "MU"} <= set(calls[2:]))
    assert feed.last("SKHY").last != first
    await asyncio.sleep(0.1)
    assert len(calls) == 4  # each refreshed once, then waits its time again
    await feed.stop()


class QuietSocket:
    """A stream that stays connected and never sends anything."""

    async def send(self, _message) -> None:
        pass

    def __aiter__(self):
        return self

    async def __anext__(self):
        await asyncio.Event().wait()


@contextlib.asynccontextmanager
async def quiet_stream(_url):
    yield QuietSocket()


async def test_quota_retries_every_interval_then_goes_offline_and_recovers():
    """Quota 429: retry every `retry`; after the 4th refusal in a row offline, then every `offline_retry`."""
    calls: list[float] = []
    refuse = {"on": True}

    def rest(request: httpx.Request) -> httpx.Response:
        calls.append(asyncio.get_running_loop().time())
        if len(calls) == 1 or not refuse["on"]:
            return httpx.Response(200, json={"c": 100 + len(calls), "pc": 100.0, "t": 1_792_000_000 + len(calls)})
        return httpx.Response(429, json={"error": "API limit reached"})

    changes: list[bool] = []
    feed = FinnhubPrices(ws_url="ws://unused", rest_url="https://finnhub.test/api/v1",
                         http=httpx.AsyncClient(transport=httpx.MockTransport(rest)), connect=quiet_stream,
                         stale_after=0.01, refresh_tick=0.01, retry=0.1, offline_retry=0.3)
    feed.on_online_change(changes.append)
    await feed.start()
    feed.set_key(KEY)
    feed.set_active(True)
    feed.subscribe({"SKHY"})
    await until(lambda: len(calls) >= 6, timeout=4)
    gaps = [b - a for a, b in zip(calls[1:], calls[2:])]
    assert all(0.1 <= g < 0.25 for g in gaps[:3])  # failures 1–3: every `retry`
    assert gaps[3] >= 0.3  # after the 4th: offline, every `offline_retry`
    assert changes == [False] and not feed.online
    refuse["on"] = False
    await until(lambda: feed.online, timeout=2)
    assert changes == [False, True]
    await feed.stop()


async def test_simulated_outage_goes_offline_and_recovers(finnhub):
    feed, fake = finnhub
    feed._retry, feed._offline_retry, feed._refresh_tick = 0.02, 0.05, 0.01
    feed.set_key(KEY)
    await until(lambda: feed.connected)
    feed.simulate_offline(True)
    await until(lambda: not feed.online)
    feed.simulate_offline(False)
    await until(lambda: feed.connected and feed.online)


async def test_unreachable_is_offline_at_once():
    def rest(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("unreachable")

    feed = FinnhubPrices(ws_url="ws://127.0.0.1:9", rest_url="https://finnhub.test/api/v1",
                         http=httpx.AsyncClient(transport=httpx.MockTransport(rest)), refresh_tick=0.01)
    await feed.start()
    feed.set_key(KEY)
    feed.set_active(True)  # nothing listens on port 9 and REST fails: offline without waiting
    feed.subscribe({"MU"})
    await until(lambda: not feed.online, timeout=1)
    feed.set_active(False)  # locked / logged out: not shown as offline
    await until(lambda: feed.online)
    await feed.stop()
