"""Price feed interface. FakePrices (random walk) now, Finnhub in T4."""

from abc import ABC, abstractmethod
from collections.abc import Callable

from trader_service.models import Contract, Price

PriceListener = Callable[[Price], None]


def price_symbol(contract: Contract) -> str:
    """Feed symbol for a contract: plain ticker for USD listings, `TICKER.EXCHANGE` otherwise
    (keeps e.g. SHOP on NYSE and SHOP on TSE apart). Provider-specific mapping is refined in T4."""
    if contract.currency == "USD":
        return contract.symbol
    return f"{contract.symbol}.{contract.primary_exchange}"


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
