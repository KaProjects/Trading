"""T11: CSV export of the trade history, one row per order."""

from datetime import UTC, date

from trader_service.models import Environment
from trader_service.transactions import transactions_csv


def fill(exec_id, side, qty, price, fees, time, order_ref="o1", symbol="NVDA", conid=4815747):
    return {"exec_id": exec_id, "order_id": 1, "order_ref": order_ref, "side": side, "quantity": qty,
            "price": price, "commission": fees, "time": time, "contract": {"conid": conid, "symbol": symbol}}


FILLS = [
    fill("e1", "BUY", "3", "145.00", "2.00", "2026-01-10T15:00:00Z"),
    fill("e2", "BUY", "2", "146.25", "2.95", "2026-01-10T15:01:00Z"),  # same order: summed
    fill("e3", "BUY", "3", "152.25", "4.95", "2026-02-15T16:00:00Z", order_ref="o2"),
    fill("e4", "SELL", "8", "180", "6.95", "2026-04-01T14:00:00Z", order_ref="o3"),
]


def test_one_row_per_order_in_portfolio_format():
    csv = transactions_csv(FILLS, date(2026, 1, 1), date(2026, 12, 31), Environment.PRODUCTION, tz=UTC)
    assert csv.splitlines() == [
        "date,type,ticker,quantity,price,fees,portfolio",
        "2026-01-10,BUY,NVDA,5,145.50,4.95,IBKR_MARGIN",
        "2026-02-15,BUY,NVDA,3,152.25,4.95,IBKR_MARGIN",
        "2026-04-01,SELL,NVDA,8,180.00,6.95,IBKR_MARGIN",
    ]


def test_range_includes_both_dates_and_paper_portfolio():
    csv = transactions_csv(FILLS, date(2026, 1, 10), date(2026, 2, 15), Environment.PAPER, tz=UTC)
    assert [line.split(",")[0] for line in csv.splitlines()[1:]] == ["2026-01-10", "2026-02-15"]
    assert csv.splitlines()[1].endswith(",IBKR_PAPER")


def test_odd_average_price_keeps_needed_decimals():
    fills = [fill("a", "BUY", "1", "10.00", "1", "2026-03-02T15:00:00Z"),
             fill("b", "BUY", "2", "10.01", "0", "2026-03-02T15:00:01Z")]
    row = transactions_csv(fills, date(2026, 3, 2), date(2026, 3, 2), Environment.PAPER, tz=UTC).splitlines()[1]
    assert row == "2026-03-02,BUY,NVDA,3,10.0067,1.00,IBKR_PAPER"
