"""Random-walk prices around seed values. Works nights and weekends and drives FakeBroker fills."""

import asyncio
import contextlib
import math
import random
from datetime import UTC, datetime
from decimal import ROUND_HALF_UP, Decimal

from trader_service.models import Price
from trader_service.prices.base import PriceFeed, PriceListener

CENT = Decimal("0.01")


class FakePrices(PriceFeed):
    def __init__(
        self,
        seed_prices: dict[str, Decimal],
        tick_interval: float | None = 1.0,
        volatility: float = 0.001,
        rng_seed: int | None = None,
    ) -> None:
        """`tick_interval=None` disables the background loop; call `tick()` manually (tests)."""
        self._prev_close = dict(seed_prices)
        self._prices: dict[str, Price] = {}
        self._symbols: set[str] = set()
        self._listeners: list[PriceListener] = []
        self._tick_interval = tick_interval
        self._volatility = volatility
        self._rng = random.Random(rng_seed)
        self._task: asyncio.Task | None = None
        for symbol, price in seed_prices.items():
            self._prices[symbol] = self._make(symbol, price)

    async def start(self) -> None:
        if self._tick_interval is not None and self._task is None:
            self._task = asyncio.create_task(self._run())

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._task
            self._task = None

    def subscribe(self, symbols: set[str]) -> None:
        self._symbols = {s for s in symbols if s in self._prev_close}

    def last(self, symbol: str) -> Price | None:
        return self._prices.get(symbol)

    def add_listener(self, listener: PriceListener) -> None:
        self._listeners.append(listener)

    def tick(self) -> None:
        for symbol in sorted(self._symbols):
            current = float(self._prices[symbol].last)
            moved = current * math.exp(self._rng.gauss(0.0, self._volatility))
            self.set_price(symbol, Decimal(str(moved)))

    def set_price(self, symbol: str, price: Decimal) -> None:
        """Force a price (tests and the developer menu)."""
        if symbol not in self._prev_close:
            self._prev_close[symbol] = price
        update = self._make(symbol, price)
        self._prices[symbol] = update
        for listener in self._listeners:
            listener(update)

    def _make(self, symbol: str, price: Decimal) -> Price:
        last = price.quantize(CENT, ROUND_HALF_UP)
        prev = self._prev_close[symbol]
        change = ((last - prev) / prev * 100).quantize(CENT, ROUND_HALF_UP)
        return Price(symbol=symbol, last=last, prev_close=prev, change_pct=change, time=datetime.now(UTC))

    async def _run(self) -> None:
        while True:
            await asyncio.sleep(self._tick_interval)
            self.tick()
