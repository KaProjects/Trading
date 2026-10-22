"""T10: executed plans and how they are doing, from saved plans + own trade history + positions + live prices."""

from collections.abc import Callable
from decimal import Decimal

from trader_service.lots import Lot, build_lots
from trader_service.models import (ExecutionLine, ExecutionReport, ExecutionRow, PlanExecution, PlanRecord,
                                   Position)

CENT = Decimal("0.01")


def _q(value: Decimal | None) -> Decimal | None:
    return value.quantize(CENT) if value is not None else None


def _difference(profit: Decimal | None, expected: Decimal | None) -> Decimal | None:
    return _q(profit - expected) if profit is not None and expected is not None else None


def _lines(group: list[Lot], price: Decimal | None, rate: Decimal, expected_pct: Decimal | None) -> list[ExecutionLine]:
    """Sales grouped per sell fill (one fill can span several lots of the row), then what is still held."""
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
                             profit=_q(profit), return_pct=_q(profit / cost * 100) if profit is not None and cost else None,
                             expected_profit=_q(expected), difference=_difference(profit, expected))

    lines = [line("sold", q, p, c, t) for (t, p), (q, c) in sorted(sold.items(), key=lambda item: item[0][0])]
    held = sum((lot.remaining for lot in group), Decimal(0))
    if held > 0:
        lines.append(line("held", held, price, sum((lot.remaining * lot.price for lot in group), Decimal(0))))
    return lines


def execution_report(plans: list[PlanRecord], fills: list[dict], positions: list[Position], base_currency: str,
                     price_of: Callable[[int], Decimal | None], fx: Callable[[str], Decimal],
                     contracts: dict[int, tuple[str, str]]) -> ExecutionReport:
    """contracts: conid → (symbol, currency) for everything that may appear."""
    plan_of_ref = {row.order_ref: plan.id for plan in plans for row in plan.rows}
    held = {p.contract.conid: (p.quantity, p.avg_cost) for p in positions}
    lots = build_lots(fills, held, plan_of_ref)

    def row(conid: int, group: list[Lot], expected_pct: Decimal | None) -> ExecutionRow:
        symbol, currency = contracts.get(conid, (str(conid), base_currency))
        rate = fx(currency)
        bought = sum((lot.quantity for lot in group), Decimal(0))
        cost_native = sum((lot.quantity * lot.price for lot in group), Decimal(0))
        remaining = sum((lot.remaining for lot in group), Decimal(0))
        realized = sum((lot.realized for lot in group), Decimal(0)) * rate
        price = price_of(conid)
        unrealized = (sum((lot.remaining * (price - lot.price) for lot in group), Decimal(0)) * rate
                      if price is not None else None)
        cost = cost_native * rate
        profit = realized + unrealized if unrealized is not None else None
        expected_profit = _q(cost * expected_pct / 100) if expected_pct is not None else None
        return ExecutionRow(
            conid=conid, symbol=symbol, currency=currency, bought=bought,
            avg_price=_q(cost_native / bought) if bought else None, held=remaining, price=price,
            cost=_q(cost), value=_q(remaining * price * rate) if price is not None else None,
            realized=_q(realized), profit=_q(profit),
            return_pct=_q(profit / cost * 100) if profit is not None and cost else None,
            expected_return_pct=expected_pct, expected_profit=expected_profit,
            difference=_difference(profit, expected_profit), lines=_lines(group, price, rate, expected_pct))

    def summarize(rows: list[ExecutionRow], **head) -> PlanExecution:
        cost = sum((r.cost for r in rows), Decimal(0))
        values = [r.value for r in rows if r.held > 0]
        value = sum(values, Decimal(0)) if None not in values else None
        profits = [r.profit for r in rows]
        profit = sum(profits, Decimal(0)) if None not in profits else None
        expected = [r.expected_profit for r in rows if r.expected_profit is not None]
        expected_profit = sum(expected, Decimal(0)) if expected else None
        bought = sum((r.bought for r in rows), Decimal(0))
        still = sum((r.held for r in rows), Decimal(0))
        status = "waiting" if bought == 0 else "closed" if still == 0 else "partly_sold" if still < bought else "open"
        return PlanExecution(
            **head, status=status, rows=rows, cost=_q(cost), value=_q(value), profit=_q(profit),
            return_pct=_q(profit / cost * 100) if profit is not None and cost else None,
            expected_profit=_q(expected_profit), difference=_difference(profit, expected_profit),
            expected_return_pct=_q(expected_profit / cost * 100) if expected_profit is not None and cost else None,
            target_reached=status in ("open", "partly_sold") and expected_profit is not None
            and profit is not None and profit >= expected_profit)

    executions = []
    for plan in sorted(plans, key=lambda p: p.placed_at, reverse=True):
        rows = [row(r.conid, [lot for lot in lots.get(r.conid, []) if lot.plan_id == plan.id], r.expected_return_pct)
                for r in plan.rows]
        executions.append(summarize(rows, id=plan.id, committed_at=plan.committed_at, placed_at=plan.placed_at))

    outside_rows = []
    for conid in sorted(lots, key=lambda c: contracts.get(c, (str(c), ""))[0]):
        group = [lot for lot in lots[conid] if lot.source != "plan"]
        if group and (sum((lot.remaining for lot in group), Decimal(0)) > 0 or any(lot.realized for lot in group)):
            outside_rows.append(row(conid, group, None))
    outside = summarize(outside_rows, id=None) if outside_rows else None
    return ExecutionReport(base_currency=base_currency, plans=executions, outside=outside)
