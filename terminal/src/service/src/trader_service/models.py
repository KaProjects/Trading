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


class PlanRecordRow(BaseModel):
    conid: int
    symbol: str
    currency: str
    order_ref: str
    planned_quantity: Decimal
    expected_return_pct: Decimal | None = None


class PlanRecord(BaseModel):
    """An executed plan (T10), saved at Place. Its fills are found in the trade history by order_ref."""

    id: str
    committed_at: datetime
    placed_at: datetime
    rows: list[PlanRecordRow] = Field(min_length=1)
    finished_at: datetime | None = None  # Finish: rows that never bought anything are dropped from Execution


class ExecutionLine(BaseModel):
    """Part of a plan row: the shares still held (at the live price) or one sale (at its sell price)."""
    kind: str                       # held | sold
    quantity: Decimal
    price: Decimal | None           # live price (held) or sell price (sold), contract currency
    time: datetime | None = None    # sell time
    cost: Decimal                   # purchase cost of these shares, base currency
    value: Decimal | None           # market value (held) or proceeds (sold), base currency
    profit: Decimal | None
    return_pct: Decimal | None
    expected_profit: Decimal | None = None
    difference: Decimal | None = None   # profit - expected profit


class ExecutionRow(BaseModel):
    conid: int
    symbol: str
    currency: str
    bought: Decimal                 # shares bought into these lots
    avg_price: Decimal | None       # average fill price (contract currency)
    held: Decimal                   # shares still held (after FIFO sells)
    price: Decimal | None           # live price (contract currency)
    cost: Decimal                   # base currency
    value: Decimal | None           # held × price, base currency
    realized: Decimal               # base currency
    profit: Decimal | None          # realized + unrealized, base currency
    return_pct: Decimal | None
    expected_return_pct: Decimal | None = None
    expected_profit: Decimal | None = None
    difference: Decimal | None = None   # profit - expected profit
    lines: list[ExecutionLine] = []     # sold lines oldest first, then the held line


class PlanExecution(BaseModel):
    id: str | None                  # None = "Outside plans" group
    committed_at: datetime | None = None
    placed_at: datetime | None = None
    status: str                     # waiting | open | partly_sold | closed
    target_reached: bool = False
    finished: bool = False          # finished by the user: rows with nothing bought are left out
    working: bool = False           # one of its orders is still open at the broker
    rows: list[ExecutionRow]
    cost: Decimal
    value: Decimal | None
    profit: Decimal | None
    return_pct: Decimal | None
    expected_profit: Decimal | None = None
    expected_return_pct: Decimal | None = None
    difference: Decimal | None = None


class ExecutionReport(BaseModel):
    base_currency: str
    plans: list[PlanExecution]      # newest first
    outside: PlanExecution | None   # outside + initial (+ correction) lots
