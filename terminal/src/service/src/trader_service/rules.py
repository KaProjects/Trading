"""Order checks (D5.3–D5.5): size and price warnings that never block, and the one hard block —
selling more than owned. Pure functions so the rules are easy to test and identical for every broker."""

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from decimal import Decimal

from trader_service.models import (
    AccountSummary,
    Contract,
    OrderBlock,
    OrderPreview,
    OrderRequest,
    OrderWarning,
    Price,
    Side,
    WarningLevel,
)

CENT = Decimal("0.01")


@dataclass(frozen=True)
class RuleSettings:
    pct_of_account_warn: Decimal = Decimal(10)
    pct_of_account_strong: Decimal = Decimal(25)
    order_value_warn: Decimal = Decimal(10_000)  # in base currency
    quantity_warn: Decimal = Decimal(1_000)
    price_deviation_warn: Decimal = Decimal(5)  # percent from last price
    price_stale_after: timedelta = timedelta(seconds=60)


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

    if request.side == Side.BUY and value_base > account.available_funds:
        warnings.append(OrderWarning(code="above_available_funds", level=WarningLevel.STRONG,
                                     message="Order value is above available funds (IBKR will reject it)"))

    if price is None:
        warnings.append(OrderWarning(code="price_missing", level=WarningLevel.WARN,
                                     message="No current price available"))
    else:
        if now - price.time > settings.price_stale_after:
            warnings.append(OrderWarning(code="price_stale", level=WarningLevel.WARN,
                                         message=f"Last price is stale ({price.time:%H:%M:%S} UTC)"))
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

    return OrderPreview(
        contract=contract, value=value.quantize(CENT), value_base=value_base.quantize(CENT),
        pct_of_account=pct.quantize(CENT) if pct is not None else None,
        last_price=price.last if price else None, warnings=warnings, block=block)
