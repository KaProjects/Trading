"""Finnhub real-time prices (T4, D4.1–D4.4).

- WebSocket `wss://ws.finnhub.io?token=…`: `{"type":"subscribe","symbol":"AAPL"}`, pushes
  `{"type":"trade","data":[{"s":"AAPL","p":228.4,"t":<ms>,"v":10}]}` → last price.
- REST `GET /quote?symbol=AAPL` once per symbol per day → previous close (day change %) and a first price
  before any trade arrives (also outside market hours).

The API key arrives from the app over the control channel (Keychain, D4.2) and is never logged.
"""

import asyncio
import contextlib
import json
import logging
from collections.abc import Callable
from datetime import UTC, date, datetime
from decimal import ROUND_HALF_UP, Decimal

import httpx
import websockets

from trader_service.models import Price
from trader_service.prices.base import PriceFeed, PriceListener

log = logging.getLogger(__name__)
CENT = Decimal("0.01")
BACKOFF = [1, 2, 5, 10, 30]


class FinnhubPrices(PriceFeed):
    def __init__(self, ws_url: str = "wss://ws.finnhub.io", rest_url: str = "https://finnhub.io/api/v1",
                 max_symbols: int = 50, http: httpx.AsyncClient | None = None,
                 connect: Callable = websockets.connect) -> None:
        self._ws_url = ws_url
        self._rest_url = rest_url
        self._max_symbols = max_symbols  # free tier limit (verify on finnhub.io)
        # REST is used a few times a day: no keep-alive, so no idle sockets linger after Finnhub closes them.
        self._http = http or httpx.AsyncClient(timeout=10, limits=httpx.Limits(max_keepalive_connections=0))
        self._connect = connect
        self._key: str | None = None
        self._key_changed = asyncio.Event()
        self._wanted: set[str] = set()
        self._streaming: set[str] = set()
        self._socket = None
        self._prices: dict[str, Price] = {}
        self._prev_close: dict[str, tuple[date, Decimal]] = {}
        self._listeners: list[PriceListener] = []
        self._tasks: set[asyncio.Task] = set()
        self._run_task: asyncio.Task | None = None
        self._active = False  # off until the app is unlocked and the broker connected (see api.py)
        self._active_changed = asyncio.Event()
        self.connected = False

    # --- PriceFeed ---------------------------------------------------------------------------------

    async def start(self) -> None:
        self._run_task = asyncio.create_task(self._run())

    async def stop(self) -> None:
        tasks = [t for t in [self._run_task, *self._tasks] if t]
        for task in tasks:
            task.cancel()
        for task in tasks:
            with contextlib.suppress(asyncio.CancelledError):
                await task
        await self._http.aclose()

    def set_key(self, key: str) -> None:
        if key and key != self._key:
            self._key = key
            self._key_changed.set()

    def set_active(self, active: bool) -> None:
        """Close the WebSocket when inactive (locked / logged out / session lost); reopen when active again."""
        if active != self._active:
            self._active = active
            self._active_changed.set()

    def subscribe(self, symbols: set[str]) -> None:
        wanted = set(sorted(symbols)[:self._max_symbols])
        if len(symbols) > self._max_symbols:
            log.warning("price feed limited to %d symbols, %d requested", self._max_symbols, len(symbols))
        new = wanted - self._wanted
        self._wanted = wanted
        for symbol in new:
            if self._active:
                self._spawn(self._fetch_quote(symbol))
        if self._socket is not None:
            self._spawn(self._sync_subscriptions())

    def last(self, symbol: str) -> Price | None:
        return self._prices.get(symbol)

    def snapshot(self) -> list[Price]:
        return [self._prices[s] for s in sorted(self._wanted) if s in self._prices]

    def add_listener(self, listener: PriceListener) -> None:
        self._listeners.append(listener)

    # --- internals -------------------------------------------------------------------------------------

    def _spawn(self, coro) -> None:
        task = asyncio.create_task(coro)
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)

    def _publish(self, symbol: str, last: Decimal, time: datetime) -> None:
        prev = self._prev_close.get(symbol, (None, last))[1]
        last = last.quantize(CENT, ROUND_HALF_UP)
        change = ((last - prev) / prev * 100).quantize(CENT, ROUND_HALF_UP) if prev else Decimal(0)
        price = Price(symbol=symbol, last=last, prev_close=prev, change_pct=change, time=time)
        self._prices[symbol] = price
        for listener in self._listeners:
            listener(price)

    async def _fetch_quote(self, symbol: str) -> None:
        """Previous close (once a day) and a first price, so the app shows something before the first trade."""
        cached = self._prev_close.get(symbol)
        if cached and cached[0] == datetime.now(UTC).date() and symbol in self._prices:
            return
        while self._key is None:
            await self._key_changed.wait()
        try:
            response = await self._http.get(f"{self._rest_url}/quote", params={"symbol": symbol},
                                             headers={"X-Finnhub-Token": self._key})
            response.raise_for_status()
            quote = response.json()
        except (httpx.HTTPError, ValueError) as error:
            log.warning("quote for %s failed: %s", symbol, type(error).__name__)
            return
        current, prev = Decimal(str(quote.get("c") or 0)), Decimal(str(quote.get("pc") or 0))
        if prev > 0:
            self._prev_close[symbol] = (datetime.now(UTC).date(), prev)
        if current > 0 and symbol not in self._prices:
            stamp = datetime.fromtimestamp(quote["t"], UTC) if quote.get("t") else datetime.now(UTC)
            self._publish(symbol, current, stamp)

    async def _sync_subscriptions(self) -> None:
        socket = self._socket
        if socket is None:
            return
        for symbol in sorted(self._wanted - self._streaming):
            await socket.send(json.dumps({"type": "subscribe", "symbol": symbol}))
            self._streaming.add(symbol)
        for symbol in sorted(self._streaming - self._wanted):
            await socket.send(json.dumps({"type": "unsubscribe", "symbol": symbol}))
            self._streaming.discard(symbol)

    async def _run(self) -> None:
        attempt = 0
        while True:
            while self._key is None or not self._active:
                self._active_changed.clear()
                waiters = [asyncio.create_task(self._key_changed.wait()), asyncio.create_task(self._active_changed.wait())]
                await asyncio.wait(waiters, return_when=asyncio.FIRST_COMPLETED)
                for waiter in waiters:
                    waiter.cancel()
            self._key_changed.clear()
            self._active_changed.clear()
            for symbol in self._wanted:  # refresh first prices / previous close after a pause
                self._spawn(self._fetch_quote(symbol))
            try:
                async with self._connect(f"{self._ws_url}?token={self._key}") as socket:
                    self._socket, self._streaming, self.connected, attempt = socket, set(), True, 0
                    await self._sync_subscriptions()
                    reader = asyncio.create_task(self._read(socket))
                    key_change = asyncio.create_task(self._key_changed.wait())
                    active_change = asyncio.create_task(self._active_changed.wait())
                    done, _ = await asyncio.wait({reader, key_change, active_change},
                                                 return_when=asyncio.FIRST_COMPLETED)
                    for task in (reader, key_change, active_change):
                        task.cancel()
                    if reader in done and not reader.cancelled() and reader.exception():
                        raise reader.exception()
            except asyncio.CancelledError:
                raise
            except Exception as error:  # network, auth, protocol: back off and retry
                log.warning("finnhub stream disconnected: %s", type(error).__name__)
            finally:
                self._socket, self.connected = None, False
            if self._key_changed.is_set() or self._active_changed.is_set() or not self._active:
                continue  # new key or paused/resumed: no back-off
            await asyncio.sleep(BACKOFF[min(attempt, len(BACKOFF) - 1)])
            attempt += 1

    async def _read(self, socket) -> None:
        async for raw in socket:
            try:
                message = json.loads(raw)
            except ValueError:
                continue
            if message.get("type") == "ping":
                continue
            if message.get("type") == "error":
                log.warning("finnhub: %s", message.get("msg"))  # e.g. a symbol it can't stream
                continue
            if message.get("type") != "trade":
                continue
            latest: dict[str, dict] = {}
            for trade in message.get("data") or []:
                symbol = trade.get("s")
                if symbol in self._wanted and (symbol not in latest or trade["t"] >= latest[symbol]["t"]):
                    latest[symbol] = trade
            for symbol, trade in latest.items():
                self._publish(symbol, Decimal(str(trade["p"])), datetime.fromtimestamp(trade["t"] / 1000, UTC))
