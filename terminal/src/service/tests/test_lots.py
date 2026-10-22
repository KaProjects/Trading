from datetime import UTC, datetime, timedelta

from conftest import D
from trader_service.lots import build_lots

T0 = datetime(2026, 10, 20, 15, 0, tzinfo=UTC)
NVDA = {"conid": 1, "symbol": "NVDA", "currency": "USD"}


def fill(exec_id, side, qty, price, minutes, ref=None, contract=NVDA):
    return {"exec_id": exec_id, "contract": contract, "side": side, "quantity": str(qty), "price": str(price),
            "time": (T0 + timedelta(minutes=minutes)).isoformat(), "order_ref": ref}


def test_initial_then_plan_then_fifo_sell():
    # 100 held before history, plan buys 30, then 110 sold: initial lot closes, 10 taken from the plan.
    fills = [fill("e1", "BUY", 30, 220, 1, ref="r-plan"), fill("e2", "SELL", 110, 230, 2)]
    lots = build_lots(fills, {1: (D(20), D(120))}, {"r-plan": "plan-3"})[1]
    initial, plan = lots
    assert (initial.source, initial.quantity, initial.remaining, initial.price) == ("initial", 100, 0, 120)
    assert initial.realized == 100 * (230 - 120)
    assert (plan.source, plan.plan_id, plan.quantity, plan.remaining) == ("plan", "plan-3", 30, 20)
    assert plan.realized == 10 * (230 - 220)


def test_outside_buys_are_their_own_lots_in_time_order():
    fills = [fill("e1", "BUY", 10, 100, 1), fill("e2", "BUY", 5, 110, 2, ref="r"), fill("e3", "SELL", 12, 120, 3)]
    lots = build_lots(fills, {1: (D(3), D(0))}, {"r": "p"})[1]
    assert [(l.source, l.remaining) for l in lots] == [("outside", 0), ("plan", 3)]


def test_two_plans_sold_oldest_first():
    fills = [fill("e1", "BUY", 10, 100, 1, ref="a"), fill("e2", "BUY", 10, 105, 2, ref="b"),
             fill("e3", "SELL", 15, 110, 3)]
    lots = build_lots(fills, {1: (D(5), D(0))}, {"a": "plan-a", "b": "plan-b"})[1]
    assert [(l.plan_id, l.remaining) for l in lots] == [("plan-a", 0), ("plan-b", 5)]


def test_correction_when_history_missed_a_sell():
    # History says 30 bought, broker holds only 20: a missed sell → correction reduces FIFO.
    lots = build_lots([fill("e1", "BUY", 30, 100, 1, ref="r")], {1: (D(20), D(100))}, {"r": "p"})[1]
    assert [(l.source, l.remaining, l.realized) for l in lots] == [("plan", 20, 0)]


def test_correction_lot_when_history_missed_a_buy():
    lots = build_lots([fill("e1", "SELL", 5, 100, 1)], {1: (D(10), D(90))}, {})[1]
    # 15 must have been held initially (10 + 5 sold) → initial lot 15, 5 sold, 10 left.
    assert [(l.source, l.quantity, l.remaining) for l in lots] == [("initial", 15, 10)]


def test_fully_sold_position_keeps_closed_lots():
    fills = [fill("e1", "BUY", 10, 100, 1, ref="r"), fill("e2", "SELL", 10, 150, 2)]
    lots = build_lots(fills, {}, {"r": "p"})[1]
    assert [(l.remaining, l.realized) for l in lots] == [(0, 500)]
