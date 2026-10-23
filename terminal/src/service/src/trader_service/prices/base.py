"""Price feed interface. FakePrices (random walk) now, Finnhub in T4."""

from abc import ABC, abstractmethod
from collections.abc import Callable

from trader_service.models import Contract, Price

PriceListener = Callable[[Price], None]


def price_symbol(contract: Contract) -> str:
    """Feed symbol for a contract (see `Contract.price_symbol`): keeps SHOP (NYSE) and SHOP.TO (TSX) apart."""
    return contract.price_symbol


class PriceFeed(ABC):
    @abstractmethod
    async def start(self) -> None: ...

    @abstractmethod
    async def stop(self) -> None: ...

    @abstractmethod
    def subscribe(self, symbols: set[str]) -> None:
        """Replace the set of streamed symbols."""

    @abstractmethod
    def last(self, symbol: str) -> Price | None: ...

    @abstractmethod
    def add_listener(self, listener: PriceListener) -> None: ...

    def set_active(self, active: bool) -> None:
        """Stream only while the app is unlocked and the broker session is connected. Default: always on."""

    @property
    def online(self) -> bool:
        """False while the price provider can't be reached (shown in the app). Default: always online."""
        return True

    def on_online_change(self, listener: Callable[[bool], None]) -> None:
        """Called with the new value whenever `online` changes. Default: never changes."""

    @abstractmethod
    def snapshot(self) -> list[Price]:
        """Latest price of every streamed symbol (the app loads this once, then follows `price` events)."""
