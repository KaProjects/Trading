from datetime import UTC, datetime, timedelta

from conftest import D
from trader_service.lots import build_lots

T0 = datetime(2026, 10, 20, 15, 0, tzinfo=UTC)
NVDA = {"conid": 1, "symbol": "NVDA", "currency": "USD"}


def fill(exec_id, side, qty, price, minutes, order_id=None, contract=NVDA):
    return {"exec_id": exec_id, "contract": contract, "side": side, "quantity": str(qty), "price": str(price),
            "time": (T0 + timedelta(minutes=minutes)).isoformat(), "order_id": order_id, "order_ref": None}


def test_initial_then_buy_then_fifo_sell():
    # 100 held before history, 30 bought, then 110 sold: initial lot closes, 10 taken from the buy.
    fills = [fill("e1", "BUY", 30, 220, 1), fill("e2", "SELL", 110, 230, 2)]
    lots = build_lots(fills, {1: (D(20), D(120))})[1]
    initial, buy = lots
    assert (initial.source, initial.quantity, initial.remaining, initial.price) == ("initial", 100, 0, 120)
    assert initial.realized == 100 * (230 - 120)
    assert (buy.source, buy.quantity, buy.remaining, buy.realized) == ("buy", 30, 20, 10 * (230 - 220))


def test_buys_sold_oldest_first():
    fills = [fill("e1", "BUY", 10, 100, 1), fill("e2", "BUY", 10, 105, 2), fill("e3", "SELL", 15, 110, 3)]
    lots = build_lots(fills, {1: (D(5), D(0))})[1]
    assert [l.remaining for l in lots] == [0, 5]


def test_fills_of_one_order_share_the_order_key():
    fills = [fill("e1", "BUY", 4, 100, 1, order_id=7), fill("e2", "BUY", 6, 101, 2, order_id=7),
             fill("e3", "BUY", 1, 102, 3)]
    lots = build_lots(fills, {1: (D(11), D(0))})[1]
    assert lots[0].order == lots[1].order != lots[2].order


def test_sold_out_ticker_starts_a_new_round():
    fills = [fill("e1", "BUY", 10, 100, 1), fill("e2", "SELL", 10, 150, 2), fill("e3", "BUY", 5, 120, 3)]
    lots = build_lots(fills, {1: (D(5), D(120))})[1]
    assert [(l.round, l.remaining, l.realized) for l in lots] == [(0, 0, 500), (1, 5, 0)]


def test_correction_when_history_missed_a_sell():
    # History says 30 bought, broker holds only 20: a missed sell → correction reduces FIFO.
    lots = build_lots([fill("e1", "BUY", 30, 100, 1)], {1: (D(20), D(100))})[1]
    assert [(l.source, l.remaining, l.realized) for l in lots] == [("buy", 20, 0)]


def test_correction_lot_when_history_missed_a_buy():
    lots = build_lots([fill("e1", "SELL", 5, 100, 1)], {1: (D(10), D(90))})[1]
    # 15 must have been held initially (10 + 5 sold) → initial lot 15, 5 sold, 10 left.
    assert [(l.source, l.quantity, l.remaining) for l in lots] == [("initial", 15, 10)]
