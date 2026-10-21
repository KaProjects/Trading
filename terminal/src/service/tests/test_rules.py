import time
from datetime import UTC, datetime, timedelta

import pytest

from conftest import D
from trader_service.models import AccountSummary, Contract, OrderRequest, Price, Side, WarningLevel
from trader_service.rules import local_time, preview_order

NOW = datetime(2026, 10, 21, 15, 0, tzinfo=UTC)
AAPL = Contract(conid=1, symbol="AAPL", name="Apple Inc", primary_exchange="NASDAQ", currency="USD")
ACCOUNT = AccountSummary(account_id="X", base_currency="USD", net_liquidation=D(100_000), total_cash=D(50_000),
                         available_funds=D(50_000), buying_power=D(50_000), cash=[])


def price(last="100", age_seconds=0) -> Price:
    return Price(symbol="AAPL", last=D(last), prev_close=D(last), change_pct=D(0),
                 time=NOW - timedelta(seconds=age_seconds))


def preview(qty="10", limit="100", side=Side.BUY, position="0", pending_sells="0", last=price(), fx="1"):
    request = OrderRequest(conid=1, side=side, quantity=D(qty), limit_price=D(limit))
    return preview_order(request, AAPL, ACCOUNT, D(fx), D(position), D(pending_sells), last, now=NOW)


def codes(result) -> dict[str, WarningLevel]:
    return {w.code: w.level for w in result.warnings}


def test_small_order_has_no_warnings():
    result = preview()
    assert result.warnings == [] and result.block is None
    assert (result.value, result.pct_of_account) == (D("1000.00"), D("1.00"))


def test_share_of_account_warn_and_strong():
    assert "pct_of_account" not in codes(preview(qty="250"))  # 25 % is not above 25 %
    assert codes(preview(qty="260"))["pct_of_account"] == WarningLevel.WARN  # 26 %
    strong = preview(qty="510", side=Side.SELL, position="600")  # 51 %
    assert codes(strong)["pct_of_account"] == WarningLevel.STRONG
    assert strong.warnings[0].message == "This order is 51 % of your account"


def test_high_value_and_quantity_never_block():
    # 105 000 value, 1 500 shares; a sell of an owned position, so no funds block either
    result = preview(qty="1500", limit="70", side=Side.SELL, position="1500", last=price("70"))
    assert {"high_value", "high_quantity"} <= codes(result).keys()
    assert result.block is None
    assert "high_value" not in codes(preview(qty="1000", limit="100", side=Side.SELL, position="1000"))


def test_value_converted_to_base_currency():
    result = preview(qty="1000", limit="100", fx="1.08", side=Side.SELL, position="1000")
    assert result.value_base == D("108000.00") and "high_value" in codes(result)


def test_buy_above_available_funds_is_blocked():
    assert preview(qty="500", limit="100").block is None  # exactly the 50 000 available
    blocked = preview(qty="501", limit="100")
    assert blocked.block.code == "insufficient_funds"
    assert blocked.block.message == "Not enough funds: order ≈ 50,100.00 USD, available 50,000.00 USD"
    assert preview(qty="600", limit="100", side=Side.SELL, position="600").block is None


def test_modified_buy_counts_its_own_reserved_funds():
    request = OrderRequest(conid=1, side=Side.BUY, quantity=D(510), limit_price=D(100))
    result = preview_order(request, AAPL, ACCOUNT, D(1), D(0), D(0), price(), now=NOW,
                           reserved_by_this_order=D(2000))
    assert result.block is None


def test_price_deviation_warns_above_5_percent_both_ways():
    assert "price_deviation" not in codes(preview(limit="105"))
    assert "price_deviation" in codes(preview(limit="105.5"))
    low = preview(limit="80")
    assert "price_deviation" in codes(low) and low.block is None
    assert "-20.0 %" in next(w.message for w in low.warnings if w.code == "price_deviation")


def test_stale_and_missing_price():
    assert "price_stale" in codes(preview(last=price(age_seconds=61)))
    assert "price_stale" not in codes(preview(last=price(age_seconds=59)))
    missing = preview(last=None)
    assert "price_missing" in codes(missing) and missing.last_price is None


def test_sell_more_than_owned_is_blocked():
    assert preview(side=Side.SELL, qty="10", position="10").block is None
    blocked = preview(side=Side.SELL, qty="11", position="10")
    assert blocked.block.code == "sell_exceeds_position"
    assert blocked.block.message == "Cannot sell 11: you own 10"


def test_open_sell_orders_count_against_position():
    blocked = preview(side=Side.SELL, qty="5", position="10", pending_sells="6")
    assert blocked.block.message == "Cannot sell 5: you own 10, 6 already in open sell orders"
    assert preview(side=Side.SELL, qty="4", position="10", pending_sells="6").block is None


def test_market_closed_is_info_not_stale():
    evening = datetime(2026, 10, 21, 21, 14, tzinfo=UTC)  # 17:14 New York, after the close
    result = preview_order(OrderRequest(conid=1, side=Side.BUY, quantity=D(1), limit_price=D(100)), AAPL, ACCOUNT,
                           D(1), D(0), D(0), price(age_seconds=3600), now=evening)
    found = {w.code: w for w in result.warnings}
    assert "price_stale" not in found
    assert found["market_closed"].level == WarningLevel.INFO
    assert found["market_closed"].message == f"Market closed · last price {local_time(NOW - timedelta(hours=1))}"


@pytest.mark.parametrize("zone, summer, winter", [
    ("Europe/Prague", "22:00 UTC+2", "21:00 UTC+1"),
    ("Asia/Kolkata", "01:30 UTC+5:30", "01:30 UTC+5:30"),
    ("America/New_York", "16:00 UTC−4", "15:00 UTC−5"),
    ("UTC", "20:00 UTC", "20:00 UTC"),
])
def test_local_time_uses_mac_zone_with_offset(monkeypatch, zone, summer, winter):
    monkeypatch.setenv("TZ", zone)
    time.tzset()
    try:
        assert local_time(datetime(2026, 10, 21, 20, 0, tzinfo=UTC)) == summer
        assert local_time(datetime(2026, 12, 1, 20, 0, tzinfo=UTC)) == winter
        assert local_time(datetime(2026, 12, 1, 20, 0, 5, tzinfo=UTC), seconds=True).startswith(winter[:5] + ":05")
    finally:
        monkeypatch.undo()
        time.tzset()


def test_us_regular_session():
    from trader_service.market_hours import us_regular_session
    assert us_regular_session(datetime(2026, 10, 21, 13, 30, tzinfo=UTC))      # 09:30 NY, Wednesday
    assert not us_regular_session(datetime(2026, 10, 21, 13, 29, tzinfo=UTC))  # 09:29
    assert not us_regular_session(datetime(2026, 10, 21, 20, 0, tzinfo=UTC))   # 16:00 close
    assert not us_regular_session(datetime(2026, 10, 24, 15, 0, tzinfo=UTC))   # Saturday
    assert us_regular_session(datetime(2026, 12, 1, 15, 0, tzinfo=UTC))        # 10:00 NY in winter time (UTC−5)
