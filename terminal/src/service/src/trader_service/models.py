"""Data shapes shared by brokers, rules and the API. Money is always Decimal (serialised as a string)."""

from datetime import datetime
from decimal import Decimal
from enum import StrEnum

from pydantic import BaseModel, Field, computed_field, model_validator


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


# IBKR primary exchange -> Finnhub symbol suffix (T4). US listings use the plain ticker.
FEED_SUFFIX = {"IBIS": "DE", "XETRA": "DE", "FWB": "F", "AEB": "AS", "SBF": "PA", "LSE": "L", "TSE": "TO",
               "VENTURE": "V", "SWX": "SW", "EBS": "SW", "BVME": "MI", "BM": "MC"}


class Contract(BaseModel):
    conid: int
    symbol: str
    name: str
    sec_type: str = "STK"
    exchange: str = "SMART"
    primary_exchange: str
    currency: str

    @computed_field
    @property
    def price_symbol(self) -> str:
        """Symbol in the price feed: `AAPL`, `SAP.DE`, `SHOP.TO` (Finnhub convention)."""
        if self.currency == "USD":
            return self.symbol
        return f"{self.symbol}.{FEED_SUFFIX.get(self.primary_exchange, self.primary_exchange)}"


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
    fx_to_base: dict[str, Decimal] = {}  # units of base currency per unit of each currency (T8 planner)


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
    INFO = "info"  # shown, not a warning (e.g. market closed)
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


class RuleThresholds(BaseModel):
    """User-configurable warning thresholds (D5.4, D5.5). Stored encrypted in settings.enc."""

    pct_of_account_warn: Decimal = Field(Decimal(25), gt=0, le=100)
    pct_of_account_strong: Decimal = Field(Decimal(50), gt=0, le=100)
    order_value_warn: Decimal = Field(Decimal(100_000), gt=0)  # base currency
    quantity_warn: Decimal = Field(Decimal(1_000), gt=0)
    price_deviation_warn: Decimal = Field(Decimal(5), gt=0, le=100)  # percent from last price
    plan_limit_markup: Decimal = Field(Decimal(3), ge=0, le=50)  # T9: committed plan → limit = last + this %

    @model_validator(mode="after")
    def strong_not_below_warn(self) -> "RuleThresholds":
        if self.pct_of_account_strong < self.pct_of_account_warn:
            raise ValueError("pct_of_account_strong must be >= pct_of_account_warn")
        return self


class Watchlist(BaseModel):
    """Extra contracts whose prices are streamed (T8 planner rows), on top of positions and open orders."""

    conids: list[int] = Field(default_factory=list, max_length=40)


class ActivityNote(BaseModel):
    """Things only the app knows about, e.g. an order dialog cancelled before sending."""

    kind: str = Field(pattern="^(order_dialog_cancelled)$")
    detail: dict = {}
