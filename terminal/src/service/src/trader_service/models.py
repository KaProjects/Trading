"""Data shapes shared by brokers, rules and the API. Money is always Decimal (serialised as a string)."""

from datetime import datetime
from decimal import Decimal
from enum import StrEnum

from pydantic import BaseModel


class Environment(StrEnum):
    FAKE = "fake"
    PAPER = "paper"
    PRODUCTION = "production"


class ConnectionState(StrEnum):
    DISCONNECTED = "disconnected"
    LOGGING_IN = "logging_in"
    CONNECTED = "connected"
    RECONNECTING = "reconnecting"
    LOGIN_REQUIRED = "login_required"
    LOGIN_FAILED = "login_failed"


class Status(BaseModel):
    state: ConnectionState
    environment: Environment
    account_id: str | None = None
    since: datetime
    message: str | None = None


class Contract(BaseModel):
    conid: int
    symbol: str
    name: str
    sec_type: str = "STK"
    exchange: str = "SMART"
    primary_exchange: str
    currency: str


class CashBalance(BaseModel):
    currency: str
    amount: Decimal


class AccountSummary(BaseModel):
    account_id: str
    base_currency: str
    net_liquidation: Decimal
    total_cash: Decimal
    available_funds: Decimal
    buying_power: Decimal
    cash: list[CashBalance]


class Position(BaseModel):
    contract: Contract
    quantity: Decimal
    avg_cost: Decimal
    cost_basis: Decimal


class Side(StrEnum):
    BUY = "BUY"
    SELL = "SELL"


class TimeInForce(StrEnum):
    DAY = "DAY"
    GTC = "GTC"


class OrderStatus(StrEnum):
    PENDING_SUBMIT = "PendingSubmit"
    SUBMITTED = "Submitted"
    FILLED = "Filled"
    CANCELLED = "Cancelled"
    REJECTED = "Rejected"

    @property
    def is_active(self) -> bool:
        return self in (OrderStatus.PENDING_SUBMIT, OrderStatus.SUBMITTED)


class OrderRequest(BaseModel):
    conid: int
    side: Side
    quantity: Decimal
    limit_price: Decimal
    tif: TimeInForce = TimeInForce.DAY
    outside_rth: bool = False


class PlaceOrderRequest(OrderRequest):
    order_ref: str


class ModifyOrderRequest(BaseModel):
    quantity: Decimal | None = None
    limit_price: Decimal | None = None


class Order(BaseModel):
    order_id: int
    order_ref: str
    contract: Contract
    side: Side
    quantity: Decimal
    limit_price: Decimal
    tif: TimeInForce
    outside_rth: bool
    status: OrderStatus
    filled: Decimal = Decimal(0)
    remaining: Decimal
    avg_fill_price: Decimal | None = None
    reject_reason: str | None = None
    created_at: datetime
    updated_at: datetime


class Fill(BaseModel):
    exec_id: str
    order_id: int | None
    order_ref: str | None
    contract: Contract
    side: Side
    quantity: Decimal
    price: Decimal
    commission: Decimal
    commission_currency: str
    time: datetime


class Price(BaseModel):
    symbol: str
    last: Decimal
    prev_close: Decimal
    change_pct: Decimal
    time: datetime


class WarningLevel(StrEnum):
    WARN = "warn"
    STRONG = "strong"


class OrderWarning(BaseModel):
    code: str
    level: WarningLevel
    message: str


class OrderBlock(BaseModel):
    code: str
    message: str


class OrderPreview(BaseModel):
    contract: Contract
    value: Decimal
    value_base: Decimal
    pct_of_account: Decimal | None
    last_price: Decimal | None
    warnings: list[OrderWarning]
    block: OrderBlock | None


class Event(BaseModel):
    type: str
    data: dict
    time: datetime
