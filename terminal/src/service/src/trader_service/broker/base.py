"""Broker adapter interface: FakeBroker (fake environment) and IbkrBroker (ib_async, paper / production)."""

from abc import ABC, abstractmethod
from decimal import Decimal

from trader_service.models import (
    AccountSummary,
    Contract,
    Fill,
    ModifyOrderRequest,
    Order,
    PlaceOrderRequest,
    Position,
    Status,
)


class BrokerError(Exception):
    code = "broker_error"


class NotConnected(BrokerError):
    code = "not_connected"


class UnknownOrder(BrokerError):
    code = "unknown_order"


class UnknownContract(BrokerError):
    code = "unknown_contract"


class OrderNotActive(BrokerError):
    code = "order_not_active"


class InvalidModification(BrokerError):
    code = "invalid_modification"


class Broker(ABC):
    """Events (status, account, position, order, fill) are published on the EventBus given at construction."""

    @abstractmethod
    async def start(self) -> None: ...

    @abstractmethod
    async def stop(self) -> None: ...

    @abstractmethod
    def status(self) -> Status: ...

    @abstractmethod
    async def login(self) -> Status: ...

    @abstractmethod
    async def logout(self) -> Status: ...

    @abstractmethod
    def account_summary(self) -> AccountSummary: ...

    @abstractmethod
    def positions(self) -> list[Position]: ...

    @abstractmethod
    def open_orders(self) -> list[Order]: ...

    @abstractmethod
    def fills(self, days: int) -> list[Fill]: ...

    @abstractmethod
    async def lookup_contract(self, symbol: str) -> list[Contract]: ...

    @abstractmethod
    def contract(self, conid: int) -> Contract: ...

    @abstractmethod
    async def place_order(self, request: PlaceOrderRequest) -> Order: ...

    @abstractmethod
    async def modify_order(self, order_id: int, request: ModifyOrderRequest) -> Order: ...

    @abstractmethod
    async def cancel_order(self, order_id: int) -> Order: ...

    @abstractmethod
    async def cancel_all(self) -> list[Order]: ...

    @abstractmethod
    def order(self, order_id: int) -> Order: ...

    @abstractmethod
    def order_by_ref(self, order_ref: str) -> Order | None:
        """Lets a retried POST /orders return the original order instead of re-running checks."""

    @abstractmethod
    def set_watchlist(self, conids: set[int]) -> None:
        """Stream prices for these contracts too (planner), besides positions and open orders."""

    @abstractmethod
    def fx_rate(self, currency: str) -> Decimal:
        """Units of base currency per one unit of `currency`."""
