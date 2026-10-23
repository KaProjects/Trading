"""T11: the trade history as CSV for the user's portfolio tracking, one row per order.

    date,type,ticker,quantity,price,fees,portfolio
    2026-01-10,BUY,NVDA,5,145.50,4.95,IBKR_MARGIN

Partial fills of one order on one day are summed: quantity, average price (weighted), fees (everything IBKR
charged: commission + exchange / regulatory fees). Dates are IBKR's trade dates when given (Flex), otherwise the
Mac's local dates; the range includes both ends.
"""

import csv
import io
from datetime import date, datetime, tzinfo
from decimal import Decimal

from trader_service.models import Environment

PORTFOLIO = {Environment.PRODUCTION: "IBKR_MARGIN", Environment.PAPER: "IBKR_PAPER", Environment.FAKE: "FAKE"}
HEADER = ["date", "type", "ticker", "quantity", "price", "fees", "portfolio"]


def _time(value) -> datetime:
    return value if isinstance(value, datetime) else datetime.fromisoformat(str(value).replace("Z", "+00:00"))


def _number(value: Decimal, min_places: int = 0, max_places: int = 4) -> str:
    """At least `min_places` decimals, more only when needed: 5, 145.50, 145.5025."""
    text = f"{value.quantize(Decimal(1).scaleb(-max_places)):f}"
    whole, _, fraction = text.partition(".")
    fraction = fraction.rstrip("0").ljust(min_places, "0")
    return f"{whole}.{fraction}" if fraction else whole


def transactions_csv(fills: list[dict], start: date, end: date, environment: Environment,
                     tz: tzinfo | None = None) -> str:
    orders: dict[tuple, dict] = {}
    for fill in fills:
        time = _time(fill["time"]).astimezone(tz)
        day = date.fromisoformat(fill["trade_date"]) if fill.get("trade_date") else time.date()
        if not start <= day <= end:
            continue
        order = fill.get("order_ref") or fill.get("order_id") or fill["exec_id"]
        key = (day, str(order), fill["side"], fill["contract"]["conid"])
        row = orders.setdefault(key, {"time": time, "symbol": fill["contract"]["symbol"], "quantity": Decimal(0),
                                      "value": Decimal(0), "fees": Decimal(0)})
        quantity = Decimal(str(fill["quantity"]))
        row["time"] = min(row["time"], time)
        row["quantity"] += quantity
        row["value"] += quantity * Decimal(str(fill["price"]))
        row["fees"] += Decimal(str(fill.get("commission") or 0))

    out = io.StringIO()
    writer = csv.writer(out, lineterminator="\n")
    writer.writerow(HEADER)
    for (day, _, side, _), row in sorted(orders.items(), key=lambda item: item[1]["time"]):
        writer.writerow([day.isoformat(), side, row["symbol"], _number(row["quantity"]),
                         _number(row["value"] / row["quantity"], 2), _number(row["fees"], 2, 2),
                         PORTFOLIO[environment]])
    return out.getvalue()
