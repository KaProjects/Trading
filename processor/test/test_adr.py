from decimal import Decimal
from unittest.mock import create_autospec

import pytest

from gemini.adr import (
    AdsRatioDetector,
    scale_to_ads,
    snap_ratio,
)
from gemini.models import Quarter
from myfinnhub.client import FinnhubClient
from polygon.client import PolygonClient
from polygon.models import TickerOverview


def make_quarter(**overrides):
    data = {
        "name": "Q3 2026",
        "id": "26Q3",
        "ending_month": "26-09",
        "report_date_previous_quarter": "2026-07-29",
        "report_date_this_quarter": "2026-10-28",
        "reported_revenues": "70000",
        "reported_eps": "59.4",
        "reported_shares": "730",
    }
    data.update(overrides)
    return Quarter(**data)


@pytest.mark.parametrize(
    ("ads", "ordinary", "expected"),
    [
        (7_288_665_960, 730_490_000, "10"),      # SKHY: 9.98
        (730_000_000, 730_000_000, "1"),
        (3_000_000_000, 6_000_000_000, "0.5"),
        (2_540_000_000, 508_000_000, "5"),
        (1_050_000_000, 100_000_000, "10"),      # 5% off still counts
    ],
)
def test_snap_ratio_picks_the_nearest_ratio_depositaries_use(
    ads,
    ordinary,
    expected,
):
    assert snap_ratio(Decimal(ads), Decimal(ordinary)) == Decimal(expected)


@pytest.mark.parametrize(
    ("ads", "ordinary"),
    [
        (6_570_000_000, 730_000_000),   # 9.0: between 8 and 10
        (1_000, 0),
        (0, 1_000),
    ],
)
def test_snap_ratio_refuses_counts_that_settle_on_no_ratio(ads, ordinary):
    assert snap_ratio(Decimal(ads), Decimal(ordinary)) is None


@pytest.fixture
def detector():
    polygon = create_autospec(PolygonClient, instance=True)
    finnhub = create_autospec(FinnhubClient, instance=True)
    return AdsRatioDetector(polygon, finnhub), polygon, finnhub


def test_an_adr_is_detected_from_the_two_share_counts(detector):
    instance, polygon, finnhub = detector
    polygon.get_ticker_overview.return_value = TickerOverview(
        type="ADRC",
        weighted_shares_outstanding=Decimal("7288665960"),
    )
    finnhub.get_shares_outstanding.return_value = Decimal("730.49")

    ratio = instance.detect("SKHY")

    assert ratio.ads_per_share == Decimal("10")
    assert ratio.ads_shares == Decimal("7288665960")
    assert ratio.ordinary_shares == Decimal("730490000")


def test_a_stock_that_is_not_an_adr_is_not_looked_up_at_finnhub(detector):
    instance, polygon, finnhub = detector
    polygon.get_ticker_overview.return_value = TickerOverview(
        type="CS",
        weighted_shares_outstanding=Decimal("7460000000"),
    )

    assert instance.detect("MSFT") is None
    finnhub.get_shares_outstanding.assert_not_called()


@pytest.mark.parametrize("shares", [None, Decimal("730")])
def test_a_ratio_of_one_or_no_count_means_nothing_to_scale(detector, shares):
    instance, polygon, finnhub = detector
    polygon.get_ticker_overview.return_value = TickerOverview(
        type="ADRC",
        weighted_shares_outstanding=Decimal("730000000"),
    )
    finnhub.get_shares_outstanding.return_value = shares

    assert instance.detect("XYZ") is None


def test_an_adr_without_a_polygon_count_is_not_detected(detector):
    instance, polygon, finnhub = detector
    polygon.get_ticker_overview.return_value = TickerOverview(type="ADRC")

    assert instance.detect("XYZ") is None
    finnhub.get_shares_outstanding.assert_not_called()


def test_provider_failures_propagate(detector):
    instance, polygon, _ = detector
    polygon.get_ticker_overview.side_effect = RuntimeError("429")

    with pytest.raises(RuntimeError, match="429"):
        instance.detect("SKHY")


def test_per_share_figures_fall_and_the_share_count_rises():
    scaled = scale_to_ads(make_quarter(), Decimal("10"))

    assert scaled.reported_eps == Decimal("5.94")
    assert scaled.reported_shares == Decimal("7300")
    # Company totals are unaffected.
    assert scaled.reported_revenues == Decimal("70000")


@pytest.mark.parametrize("ratio", [None, Decimal("1")])
def test_no_scaling_without_a_ratio(ratio):
    quarter = make_quarter()

    assert scale_to_ads(quarter, ratio) is quarter


def test_missing_per_share_figures_stay_missing():
    scaled = scale_to_ads(
        make_quarter(reported_eps=None, reported_shares=None),
        Decimal("10"),
    )

    assert scaled.reported_eps is None
    assert scaled.reported_shares is None
