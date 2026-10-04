import logging
from datetime import date
from decimal import Decimal
from unittest.mock import Mock, call, create_autospec

import pytest

from error_reporting import ErrorReporter
from gemini.service import FirebaseService as GeminiFirebaseService
from indicators.models import PriceIndicator
from indicators.service import FirebaseService as IndicatorsFirebaseService
from polygon.client import PolygonClient, PolygonClientError
from polygon.models import GroupedDailyResponse
from polygon.prices_retriever import (
    MAX_WEEKDAY_LOOKBACK,
    PolygonPricesRetrieverRunner,
)

TUESDAY = date(2026, 10, 6)
MONDAY = date(2026, 10, 5)
FRIDAY = date(2026, 10, 2)


def grouped(*closes):
    return GroupedDailyResponse.model_validate({
        "results": [{"T": ticker, "c": close} for ticker, close in closes],
    })


@pytest.fixture
def runner():
    instance = PolygonPricesRetrieverRunner(
        client=create_autospec(PolygonClient, instance=True),
        gemini_service=create_autospec(GeminiFirebaseService, instance=True),
        indicators_service=create_autospec(
            IndicatorsFirebaseService,
            instance=True,
        ),
        error_reporter=create_autospec(ErrorReporter, instance=True),
        clock=lambda: TUESDAY,
    )
    instance.log = create_autospec(logging.Logger, instance=True)
    return instance


def test_requires_an_api_key_without_a_client():
    with pytest.raises(ValueError, match="polygon_api_key is required"):
        PolygonPricesRetrieverRunner()


def test_persists_the_previous_sessions_close_for_each_tracked_company(
    runner,
):
    runner.gemini_service.get_companies.return_value = {
        "MU": Mock(),
        "BRK.B": Mock(),
        "NEWC": None,
    }
    runner.client.get_grouped_daily.return_value = grouped(
        ("MU", "1074.89"),
        ("BRK.B", "502.65"),
        ("AAPL", "200"),
    )

    runner.run()

    runner.client.get_grouped_daily.assert_called_once_with(MONDAY)
    # The stored date is the retrieval day (today), not the session's day.
    assert runner.indicators_service.upsert_price.call_args_list == [
        call("BRK.B", PriceIndicator(value=Decimal("502.65"), retrieved_on=TUESDAY)),
        call("MU", PriceIndicator(value=Decimal("1074.89"), retrieved_on=TUESDAY)),
    ]
    runner.errors.report_warning_message.assert_not_called()
    runner.errors.report.assert_not_called()


@pytest.mark.parametrize(
    ("today", "expected"),
    [
        (date(2026, 10, 6), date(2026, 10, 5)),
        (date(2026, 10, 5), date(2026, 10, 2)),
        (date(2026, 10, 4), date(2026, 10, 2)),
        (date(2026, 10, 3), date(2026, 10, 2)),
    ],
)
def test_previous_weekday_skips_weekends(today, expected):
    assert PolygonPricesRetrieverRunner._previous_weekday(today) == expected


def test_steps_back_over_a_market_holiday(runner):
    runner.gemini_service.get_companies.return_value = {"MU": Mock()}
    runner.client.get_grouped_daily.side_effect = [
        grouped(),
        grouped(("MU", "1074.89")),
    ]

    runner.run()

    assert runner.client.get_grouped_daily.call_args_list == [
        call(MONDAY),
        call(FRIDAY),
    ]
    runner.indicators_service.upsert_price.assert_called_once_with(
        "MU",
        PriceIndicator(value=Decimal("1074.89"), retrieved_on=TUESDAY),
    )
    # Never silent: a stepped-back close would otherwise look current.
    runner.errors.report_warning_message.assert_called_once()
    warning = runner.errors.report_warning_message.call_args
    assert "2026-10-05" in warning.args[0]
    assert "2026-10-02" in warning.args[0]
    assert warning.kwargs["context"] == {
        "requested_date": "2026-10-05",
        "used_date": "2026-10-02",
    }


def test_missing_tickers_are_warned_once_and_found_ones_still_stored(runner):
    runner.gemini_service.get_companies.return_value = {
        "KRX": Mock(),
        "MU": Mock(),
        "ZZZ": Mock(),
    }
    runner.client.get_grouped_daily.return_value = grouped(("MU", "1074.89"))

    runner.run()

    runner.indicators_service.upsert_price.assert_called_once_with(
        "MU",
        PriceIndicator(value=Decimal("1074.89"), retrieved_on=TUESDAY),
    )
    runner.errors.report_warning_message.assert_called_once()
    warning = runner.errors.report_warning_message.call_args
    assert "KRX, ZZZ" in warning.args[0]
    assert warning.kwargs["operation"] == "retrieve_prices"
    assert warning.kwargs["context"] == {
        "date": "2026-10-05",
        "missing_count": 2,
    }
    runner.errors.report.assert_not_called()


def test_one_failed_persist_does_not_stop_the_others(runner):
    runner.gemini_service.get_companies.return_value = {
        "AMD": Mock(),
        "MU": Mock(),
    }
    runner.client.get_grouped_daily.return_value = grouped(
        ("AMD", "633.91"),
        ("MU", "1074.89"),
    )
    failure = RuntimeError("firebase unavailable")
    runner.indicators_service.upsert_price.side_effect = [failure, None]

    runner.run()

    assert runner.indicators_service.upsert_price.call_count == 2
    runner.errors.report.assert_called_once_with(
        failure,
        logger=runner.log,
        source=runner.name,
        operation="persist_price",
        context={"ticker": "AMD", "retrieved_on": "2026-10-06"},
    )


def test_reports_an_error_when_no_session_is_found(runner):
    runner.gemini_service.get_companies.return_value = {"MU": Mock()}
    runner.client.get_grouped_daily.return_value = grouped()

    runner.run()

    assert (
        runner.client.get_grouped_daily.call_count == MAX_WEEKDAY_LOOKBACK
    )
    runner.indicators_service.upsert_price.assert_not_called()
    runner.errors.report_error_message.assert_called_once()
    assert (
        runner.errors.report_error_message.call_args.kwargs["operation"]
        == "retrieve_prices"
    )


def test_unexpected_failure_is_reported_and_stores_nothing(runner):
    runner.gemini_service.get_companies.return_value = {"MU": Mock()}
    failure = PolygonClientError("Polygon request failed")
    runner.client.get_grouped_daily.side_effect = failure

    runner.run()

    runner.indicators_service.upsert_price.assert_not_called()
    runner.errors.report.assert_called_once_with(
        failure,
        logger=runner.log,
        source=runner.name,
        operation="run",
        context=None,
    )


def test_makes_no_api_call_without_initialized_companies(runner):
    runner.gemini_service.get_companies.return_value = {"NEWC": None}

    runner.run()

    runner.client.get_grouped_daily.assert_not_called()
    runner.indicators_service.upsert_price.assert_not_called()
