"""T12: every position per ticker and how it is doing, from own trade history + positions + live prices.

A position runs from the first buy of a ticker until it is sold down to 0 (then it moves to History). Plans
only provide the expected return: the latest placed plan with that ticker, within the position's lifetime.
"""

from collections.abc import Callable
from datetime import datetime
from decimal import Decimal

from trader_service.lots import EPOCH, Lot, build_lots
from trader_service.models import (ExecutionLine, ExecutionReport, ExecutionRow, ExecutionTotal, PlanRecord,
                                   Position, Purchase)

CENT = Decimal("0.01")


def _q(value: Decimal | None) -> Decimal | None:
    return value.quantize(CENT) if value is not None else None


def _difference(profit: Decimal | None, expected: Decimal | None) -> Decimal | None:
    return _q(profit - expected) if profit is not None and expected is not None else None


def _pct(profit: Decimal | None, cost: Decimal) -> Decimal | None:
    return _q(profit / cost * 100) if profit is not None and cost else None


def _lines(group: list[Lot], price: Decimal | None, rate: Decimal, expected_pct: Decimal | None) -> list[ExecutionLine]:
    """Sales grouped per sell fill (one fill can span several lots), then what is still held."""
    sold: dict[tuple, list[Decimal]] = {}  # (time, price) -> [shares, purchase cost]
    for lot in group:
        for sale in lot.sales:
            entry = sold.setdefault((sale.time, sale.price), [Decimal(0), Decimal(0)])
            entry[0] += sale.quantity
            entry[1] += sale.quantity * lot.price

    def line(kind, quantity, at, cost_native, time=None) -> ExecutionLine:
        cost = cost_native * rate
        value = quantity * at * rate if at is not None else None
        profit = value - cost if value is not None else None
        expected = cost * expected_pct / 100 if expected_pct is not None else None
        return ExecutionLine(kind=kind, quantity=quantity, price=at, time=time, cost=_q(cost), value=_q(value),
                             profit=_q(profit), return_pct=_pct(profit, cost),
                             expected_profit=_q(expected), difference=_difference(profit, expected))

    lines = [line("sold", q, p, c, t) for (t, p), (q, c) in sorted(sold.items(), key=lambda item: item[0][0])]
    held = sum((lot.remaining for lot in group), Decimal(0))
    if held > 0:
        lines.append(line("held", held, price, sum((lot.remaining * lot.price for lot in group), Decimal(0))))
    return lines


def _purchases(group: list[Lot], rate: Decimal) -> list[Purchase]:
    """Buy lots summed per order; initial and correction lots are shares held before the recorded history."""
    by_order: dict[str, list[Lot]] = {}
    for lot in group:
        by_order.setdefault(lot.order or lot.source, []).append(lot)
    purchases = []
    for lots in by_order.values():
        quantity = sum((lot.quantity for lot in lots), Decimal(0))
        cost = sum((lot.quantity * lot.price for lot in lots), Decimal(0))
        first = min(lot.time for lot in lots)
        purchases.append(Purchase(time=first if lots[0].source == "buy" else None, quantity=quantity,
                                  price=_q(cost / quantity), cost=_q(cost * rate)))
    return sorted(purchases, key=lambda p: p.time or EPOCH)


def _expected(plans: list[PlanRecord], conid: int, since: datetime, until: datetime | None) \
        -> tuple[Decimal | None, datetime | None]:
    """The expected return from the latest plan placed within the position's lifetime."""
    best = None
    for plan in plans:
        if plan.placed_at < since or (until is not None and plan.placed_at > until):
            continue
        for row in plan.rows:
            if row.conid == conid and row.expected_return_pct is not None:
                if best is None or plan.placed_at > best[1]:
                    best = (row.expected_return_pct, plan.placed_at)
    return best or (None, None)


def _total(rows: list[ExecutionRow]) -> ExecutionTotal:
    cost = sum((r.cost for r in rows), Decimal(0))
    values = [r.value for r in rows]
    value = sum(values, Decimal(0)) if None not in values else None
    profits = [r.profit for r in rows]
    profit = sum(profits, Decimal(0)) if None not in profits else None
    expected = [r.expected_profit for r in rows if r.expected_profit is not None]
    expected_profit = sum(expected, Decimal(0)) if expected else None
    return ExecutionTotal(cost=_q(cost), value=_q(value), profit=_q(profit), return_pct=_pct(profit, cost),
                          expected_profit=_q(expected_profit), expected_return_pct=_pct(expected_profit, cost),
                          difference=_difference(profit, expected_profit))


def execution_report(plans: list[PlanRecord], fills: list[dict], positions: list[Position], base_currency: str,
                     price_of: Callable[[int], Decimal | None], fx: Callable[[str], Decimal],
                     contracts: dict[int, tuple[str, str]]) -> ExecutionReport:
    """contracts: conid → (symbol, currency) for everything that may appear."""
    held = {p.contract.conid: (p.quantity, p.avg_cost) for p in positions}
    lots = build_lots(fills, held)

    def row(conid: int, group: list[Lot], since: datetime) -> ExecutionRow:
        symbol, currency = contracts.get(conid, (str(conid), base_currency))
        rate = fx(currency)
        bought = sum((lot.quantity for lot in group), Decimal(0))
        cost_native = sum((lot.quantity * lot.price for lot in group), Decimal(0))
        remaining = sum((lot.remaining for lot in group), Decimal(0))
        realized = sum((lot.realized for lot in group), Decimal(0)) * rate
        sales = [sale.time for lot in group for sale in lot.sales]
        closed_at = max(sales, default=None) if remaining == 0 else None
        price = price_of(conid) if remaining > 0 else None
        unrealized = (sum((lot.remaining * (price - lot.price) for lot in group), Decimal(0)) * rate
                      if price is not None else Decimal(0) if remaining == 0 else None)
        cost = cost_native * rate
        profit = realized + unrealized if unrealized is not None else None
        expected_pct, expected_from = _expected(plans, conid, since, closed_at)
        expected_profit = _q(cost * expected_pct / 100) if expected_pct is not None else None
        return ExecutionRow(
            conid=conid, symbol=symbol, currency=currency, bought=bought,
            avg_price=_q(cost_native / bought) if bought else None, held=remaining, price=price,
            cost=_q(cost), value=_q(remaining * price * rate) if price is not None else None,
            realized=_q(realized), profit=_q(profit), return_pct=_pct(profit, cost),
            expected_return_pct=expected_pct, expected_from=expected_from, expected_profit=expected_profit,
            difference=_difference(profit, expected_profit), purchases=_purchases(group, rate),
            lines=_lines(group, price, rate, expected_pct), closed_at=closed_at)

    open_rows, closed_rows = [], []
    for conid, ticker_lots in lots.items():
        since = EPOCH  # a round's plans: placed after the previous round was sold out
        for round_ in sorted({lot.round for lot in ticker_lots}):
            group = [lot for lot in ticker_lots if lot.round == round_]
            if not any(lot.sales for lot in group) and all(lot.remaining <= 0 for lot in group):
                continue  # only corrected away, never really held or sold
            r = row(conid, group, since)
            if r.held > 0:
                open_rows.append(r)
            else:
                closed_rows.append(r)
                since = r.closed_at or since
    open_rows.sort(key=lambda r: r.symbol)
    closed_rows.sort(key=lambda r: r.closed_at or EPOCH, reverse=True)
    return ExecutionReport(base_currency=base_currency, positions=open_rows,
                           total=_total(open_rows) if open_rows else None, closed=closed_rows)
