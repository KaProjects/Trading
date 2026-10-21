"""Order checks (D5.3–D5.5): size and price warnings that never block, and two hard blocks —
selling more than owned and buying for more than the available funds. Pure functions so the rules are easy to test and identical for every broker."""

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from decimal import Decimal

from trader_service.market_hours import us_regular_session
from trader_service.models import (
    AccountSummary,
    Contract,
    OrderBlock,
    OrderPreview,
    OrderRequest,
    OrderWarning,
    Price,
    RuleThresholds,
    Side,
    WarningLevel,
)

CENT = Decimal("0.01")


def local_time(moment: datetime, seconds: bool = False) -> str:
    """The service runs on the user's Mac: its time zone, with the offset ("22:00 UTC+2")."""
    local = moment.astimezone()
    minutes = int((local.utcoffset() or timedelta(0)).total_seconds() // 60)
    hours, rest = divmod(abs(minutes), 60)
    zone = "UTC" if minutes == 0 else f"UTC{'+' if minutes > 0 else '−'}{hours}" + (f":{rest:02d}" if rest else "")
    return f"{local.strftime('%H:%M:%S' if seconds else '%H:%M')} {zone}"


@dataclass(frozen=True)
class RuleSettings:
    pct_of_account_warn: Decimal = Decimal(25)
    pct_of_account_strong: Decimal = Decimal(50)
    order_value_warn: Decimal = Decimal(100_000)  # in base currency
    quantity_warn: Decimal = Decimal(1_000)
    price_deviation_warn: Decimal = Decimal(5)  # percent from last price
    price_stale_after: timedelta = timedelta(seconds=60)

    @classmethod
    def from_thresholds(cls, t: RuleThresholds) -> "RuleSettings":
        return cls(pct_of_account_warn=t.pct_of_account_warn, pct_of_account_strong=t.pct_of_account_strong,
                   order_value_warn=t.order_value_warn, quantity_warn=t.quantity_warn,
                   price_deviation_warn=t.price_deviation_warn)


def preview_order(
    request: OrderRequest,
    contract: Contract,
    account: AccountSummary,
    fx_to_base: Decimal,
    position_quantity: Decimal,
    pending_sell_quantity: Decimal,
    price: Price | None,
    settings: RuleSettings = RuleSettings(),
    now: datetime | None = None,
    reserved_by_this_order: Decimal = Decimal(0),
) -> OrderPreview:
    now = now or datetime.now(UTC)
    value = request.quantity * request.limit_price
    value_base = value * fx_to_base
    pct = (value_base / account.net_liquidation * 100) if account.net_liquidation > 0 else None
    warnings: list[OrderWarning] = []

    if pct is not None and pct > settings.pct_of_account_strong:
        warnings.append(OrderWarning(code="pct_of_account", level=WarningLevel.STRONG,
                                     message=f"This order is {pct:.0f} % of your account"))
    elif pct is not None and pct > settings.pct_of_account_warn:
        warnings.append(OrderWarning(code="pct_of_account", level=WarningLevel.WARN,
                                     message=f"This order is {pct:.0f} % of your account"))

    if value_base > settings.order_value_warn:
        warnings.append(OrderWarning(code="high_value", level=WarningLevel.WARN,
                                     message=f"High order value: {value_base:,.2f} {account.base_currency}"))

    if request.quantity > settings.quantity_warn:
        warnings.append(OrderWarning(code="high_quantity", level=WarningLevel.WARN,
                                     message=f"High quantity: {request.quantity:,} shares"))

    if price is None:
        warnings.append(OrderWarning(code="price_missing", level=WarningLevel.WARN,
                                     message="No current price available"))
    else:
        if not us_regular_session(now):
            warnings.append(OrderWarning(code="market_closed", level=WarningLevel.INFO,
                                         message=f"Market closed · last price {local_time(price.time)}"))
        elif now - price.time > settings.price_stale_after:
            warnings.append(OrderWarning(code="price_stale", level=WarningLevel.WARN,
                                         message=f"Last price is stale ({local_time(price.time, seconds=True)})"))
        deviation = (request.limit_price - price.last) / price.last * 100
        if abs(deviation) > settings.price_deviation_warn:
            warnings.append(OrderWarning(code="price_deviation", level=WarningLevel.WARN,
                                         message=f"Limit price is {deviation:+.1f} % from last price {price.last}"))

    block = None
    sellable = position_quantity - pending_sell_quantity
    if request.side == Side.SELL and request.quantity > sellable:
        detail = f"you own {position_quantity}"
        if pending_sell_quantity:
            detail += f", {pending_sell_quantity} already in open sell orders"
        block = OrderBlock(code="sell_exceeds_position", message=f"Cannot sell {request.quantity}: {detail}")

    # A modified buy already reserves funds; count them back in so it isn't blocked by itself.
    available = account.available_funds + reserved_by_this_order
    if request.side == Side.BUY and value_base > available:
        block = OrderBlock(code="insufficient_funds",
                           message=f"Not enough funds: order ≈ {value_base:,.2f} {account.base_currency}, "
                                   f"available {available:,.2f} {account.base_currency}")

    return OrderPreview(
        contract=contract, value=value.quantize(CENT), value_base=value_base.quantize(CENT),
        pct_of_account=pct.quantize(CENT) if pct is not None else None,
        last_price=price.last if price else None, warnings=warnings, block=block)
