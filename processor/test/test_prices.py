from datetime import date
from decimal import Decimal
from unittest.mock import create_autospec

import pytest

from gemini.models import Info
from gemini.prices import (
    PriceRange,
    QuarterPriceRanges,
    prices_come_from_polygon,
    trading_currency,
)
from polygon.client import DailyRange, PolygonClient


def bar(day, low, high):
    return DailyRange(day=day, low=Decimal(str(low)), high=Decimal(str(high)))


@pytest.fixture
def client():
    return create_autospec(PolygonClient, instance=True)


def make_info(currency="$", override=None):
    return Info(
        ticker="X",
        currency=currency,
        currency_override=override,
        last_update="2026-10-01",
        current_quarter_id="26Q3",
    )


def test_range_is_the_lowest_low_and_highest_high_between_the_report_dates(
    client,
):
    client.get_daily_ranges.return_value = [
        bar(date(2026, 4, 30), 300, 500),   # the previous report day
        bar(date(2026, 5, 1), 349.2, 400),
        bar(date(2026, 5, 4), 360, 466.32),
        bar(date(2026, 7, 28), 10, 20),     # the report day itself
    ]

    ranges = QuarterPriceRanges(client).ranges(
        "MSFT",
        {"26Q4": (date(2026, 4, 30), date(2026, 7, 28))},
    )

    assert ranges == {
        "26Q4": PriceRange(low=Decimal("349.2"), high=Decimal("466.32")),
    }


def test_one_request_covers_every_quarter_window(client):
    client.get_daily_ranges.return_value = [
        bar(date(2026, 2, 10), 90, 110),
        bar(date(2026, 5, 10), 100, 140),
    ]

    ranges = QuarterPriceRanges(client).ranges(
        "NVDA",
        {
            "26Q1": (date(2026, 2, 1), date(2026, 4, 30)),
            "26Q2": (date(2026, 4, 30), date(2026, 7, 30)),
        },
    )

    client.get_daily_ranges.assert_called_once_with(
        "NVDA",
        date(2026, 2, 1),
        date(2026, 7, 30),
    )
    assert ranges["26Q1"] == PriceRange(Decimal("90"), Decimal("110"))
    assert ranges["26Q2"] == PriceRange(Decimal("100"), Decimal("140"))


def test_a_window_without_sessions_has_no_range(client):
    client.get_daily_ranges.return_value = [
        bar(date(2026, 8, 1), 20, 25),
    ]

    ranges = QuarterPriceRanges(client).ranges(
        "SKHY",
        {
            "26Q2": (date(2026, 4, 1), date(2026, 7, 29)),
            "26Q3": (date(2026, 7, 29), date(2026, 10, 28)),
        },
    )

    assert ranges["26Q2"] is None
    assert ranges["26Q3"] == PriceRange(Decimal("20"), Decimal("25"))


def test_no_windows_means_no_request(client):
    assert QuarterPriceRanges(client).ranges("X", {}) == {}
    client.get_daily_ranges.assert_not_called()


def test_a_polygon_failure_propagates(client):
    client.get_daily_ranges.side_effect = RuntimeError("polygon down")

    with pytest.raises(RuntimeError, match="polygon down"):
        QuarterPriceRanges(client).ranges(
            "X",
            {"26Q1": (date(2026, 1, 1), date(2026, 4, 1))},
        )


@pytest.mark.parametrize(
    ("currency", "override", "expected_currency", "polygon"),
    [
        ("$", None, "$", True),
        ("€", None, "€", False),
        ("₩", None, "₩", False),
        ("₩", "$", "$", True),
        ("$", "€", "€", False),
    ],
)
def test_prices_come_from_polygon_only_for_dollar_trading_currency(
    currency,
    override,
    expected_currency,
    polygon,
):
    info = make_info(currency, override)

    assert trading_currency(info) == expected_currency
    assert prices_come_from_polygon(info) is polygon
