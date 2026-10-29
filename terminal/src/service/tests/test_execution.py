"""T12: positions per ticker, purchases, rounds and expected returns from plans."""

from datetime import UTC, datetime, timedelta

from conftest import D
from test_lots import NVDA, T0, fill
from trader_service.execution import execution_report
from trader_service.models import Contract, PlanRecord, Position

AAPL = {"conid": 2, "symbol": "AAPL", "currency": "USD"}


def plan(minutes, conid, pct, plan_id="p"):
    placed = (T0 + timedelta(minutes=minutes)).isoformat()
    return PlanRecord(id=plan_id, committed_at=placed, placed_at=placed,
                      rows=[{"conid": conid, "symbol": "X", "currency": "USD", "order_ref": f"{plan_id}-r",
                             "planned_quantity": "1", "expected_return_pct": pct}])


def position(conid, quantity, avg):
    return Position(contract=Contract(conid=conid, symbol="X", name="X", primary_exchange="NASDAQ", currency="USD"),
                    quantity=D(quantity), avg_cost=D(avg), cost_basis=D(quantity) * D(avg))


def report(plans, fills, positions, prices):
    return execution_report(plans, fills, positions, "USD", prices.get, lambda c: D(1),
                            {1: ("NVDA", "USD"), 2: ("AAPL", "USD")})


def test_one_position_per_ticker_with_purchases_and_latest_expected_return():
    fills = [fill("e1", "BUY", 4, 100, 1, order_id=7), fill("e2", "BUY", 6, 110, 2, order_id=7),  # one order
             fill("e3", "BUY", 10, 120, 60), fill("e4", "SELL", 5, 130, 90),
             fill("a1", "BUY", 2, 50, 5, contract=AAPL)]
    plans = [plan(0, 1, "10", "p1"), plan(59, 1, "20", "p2")]
    r = report(plans, fills, [position(1, 15, 113), position(2, 2, 50)], {1: D(140), 2: D(55)})
    aapl, nvda = r.positions
    assert (aapl.symbol, nvda.symbol) == ("AAPL", "NVDA")
    assert [(p.quantity, p.price) for p in nvda.purchases] == [(10, D("106.00")), (10, D("120.00"))]
    assert (nvda.bought, nvda.held, nvda.cost, nvda.value) == (20, 15, D("2260.00"), D("2100.00"))
    assert [(l.kind, l.quantity) for l in nvda.lines] == [("sold", 5), ("held", 15)]
    assert nvda.expected_return_pct == 20 and nvda.expected_from == T0 + timedelta(minutes=59)
    assert nvda.expected_profit == D("452.00") and aapl.expected_return_pct is None
    assert r.total.cost == D("2360.00") and r.total.expected_profit == D("452.00")
    assert r.total.value == D("2860.00")  # NVDA 650 sold + 2 100 active, AAPL 110 active
    assert r.closed == []
    # FIFO trades: the 5 sold came from the first order (4 @ 100 + 1 @ 110), the rest is active.
    sold, active_first, active_second = nvda.trades
    assert (sold.quantity, sold.buy_price, sold.sell_price, sold.profit) == (5, D("102.00"), 130, D("140.00"))
    assert (active_first.quantity, active_first.buy_price, active_first.sold_at) == (5, D("110.00"), None)
    assert (active_second.quantity, active_second.bought_at) == (10, T0 + timedelta(minutes=60))


def test_sold_out_position_goes_to_history_and_a_new_buy_starts_fresh():
    fills = [fill("e1", "BUY", 10, 100, 1), fill("e2", "SELL", 10, 150, 30), fill("e3", "BUY", 5, 120, 60)]
    plans = [plan(0, 1, "25")]  # placed before the first round: not the new position's
    r = report(plans, fills, [position(1, 5, 120)], {1: D(130)})
    [now], [old] = r.positions, r.closed
    assert (now.bought, now.expected_return_pct, len(now.purchases)) == (5, None, 1)
    assert (old.held, old.profit, old.return_pct, old.expected_profit) == (0, D("500.00"), D("50.00"), D("250.00"))
    assert old.closed_at == T0 + timedelta(minutes=30) and old.value is None


def test_shares_held_before_history_are_a_purchase_without_date():
    r = report([], [], [position(1, 3, 90)], {1: D(100)})
    [p] = r.positions[0].purchases
    assert (p.time, p.quantity, p.price) == (None, 3, D("90.00"))
