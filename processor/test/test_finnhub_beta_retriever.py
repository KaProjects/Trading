import logging
from datetime import date
from decimal import Decimal
from unittest.mock import Mock, call, create_autospec

import pytest

from error_reporting import ErrorReporter
from gemini.service import FirebaseService as GeminiFirebaseService
from indicators.models import BetaIndicator
from indicators.service import FirebaseService as IndicatorsFirebaseService
from myfinnhub.beta_retriever import (
    REQUEST_SPACING_SECONDS,
    FinnhubBetaRetrieverRunner,
)
from myfinnhub.client import FinnhubClient

TODAY = date(2026, 10, 23)


@pytest.fixture
def runner():
    instance = FinnhubBetaRetrieverRunner(
        client=create_autospec(FinnhubClient, instance=True),
        gemini_service=create_autospec(GeminiFirebaseService, instance=True),
        indicators_service=create_autospec(
            IndicatorsFirebaseService,
            instance=True,
        ),
        error_reporter=create_autospec(ErrorReporter, instance=True),
        sleeper=Mock(),
        clock=lambda: TODAY,
    )
    instance.log = create_autospec(logging.Logger, instance=True)
    return instance


def tracked(runner, *tickers, placeholders=()):
    runner.gemini_service.get_companies.return_value = {
        **{ticker: object() for ticker in tickers},
        **{ticker: None for ticker in placeholders},
    }


def test_requires_an_api_key_without_a_client():
    with pytest.raises(ValueError, match="finnhub_api_key is required"):
        FinnhubBetaRetrieverRunner()


def test_stores_a_dated_beta_for_every_initialized_company(runner):
    tracked(runner, "CRDO", "MSFT", placeholders=["NEW"])
    runner.client.get_beta.side_effect = [Decimal("3.27"), Decimal("1.06")]

    runner.run()

    assert runner.client.get_beta.call_args_list == [
        call("CRDO"),
        call("MSFT"),
    ]
    assert runner.indicators_service.upsert_beta.call_args_list == [
        call("CRDO", BetaIndicator(value=Decimal("3.27"), retrieved_on=TODAY)),
        call("MSFT", BetaIndicator(value=Decimal("1.06"), retrieved_on=TODAY)),
    ]
    runner.errors.report.assert_not_called()
    runner.errors.report_error_message.assert_not_called()
    runner.errors.report_warning_message.assert_not_called()


def test_calls_are_spaced_to_respect_the_rate_limit(runner):
    tracked(runner, "A", "B", "C")
    runner.client.get_beta.return_value = Decimal("1")

    runner.run()

    # Between calls, not before the first or after the last.
    assert runner.sleeper.call_args_list == [
        call(REQUEST_SPACING_SECONDS),
        call(REQUEST_SPACING_SECONDS),
    ]


def test_no_companies_means_no_calls(runner):
    tracked(runner)

    runner.run()

    runner.client.get_beta.assert_not_called()


def test_unavailable_betas_are_one_warning_and_the_rest_are_stored(runner):
    tracked(runner, "AAA", "NEW1", "NEW2")
    runner.client.get_beta.side_effect = [Decimal("1"), None, None]

    runner.run()

    runner.indicators_service.upsert_beta.assert_called_once()
    runner.errors.report_warning_message.assert_called_once()
    message = runner.errors.report_warning_message.call_args.args[0]
    assert "NEW1, NEW2" in message
    runner.errors.report_error_message.assert_not_called()


def test_one_failing_ticker_does_not_cost_the_others(runner):
    tracked(runner, "AAA", "BBB", "CCC")
    runner.client.get_beta.side_effect = [
        Decimal("1"),
        RuntimeError("429 rate limited"),
        Decimal("2"),
    ]

    runner.run()

    stored = [
        c.args[0] for c in runner.indicators_service.upsert_beta.call_args_list
    ]
    assert stored == ["AAA", "CCC"]
    runner.errors.report_error_message.assert_called_once()
    message = runner.errors.report_error_message.call_args.args[0]
    assert "BBB (RuntimeError: 429 rate limited)" in message
    assert "AAA" not in message


def test_a_failing_write_is_reported_and_isolated(runner):
    tracked(runner, "AAA", "BBB")
    runner.client.get_beta.return_value = Decimal("1")
    runner.indicators_service.upsert_beta.side_effect = [
        RuntimeError("firebase down"),
        None,
    ]

    runner.run()

    assert runner.indicators_service.upsert_beta.call_count == 2
    message = runner.errors.report_error_message.call_args.args[0]
    assert "AAA (RuntimeError: firebase down)" in message


def test_an_outage_is_a_single_error_not_one_per_company(runner):
    tracked(runner, "A", "B", "C", "D")
    runner.client.get_beta.side_effect = RuntimeError("api down")

    runner.run()

    runner.errors.report_error_message.assert_called_once()
    runner.indicators_service.upsert_beta.assert_not_called()


def test_unreadable_companies_are_reported_not_raised(runner):
    runner.gemini_service.get_companies.side_effect = RuntimeError("db down")

    runner.run()

    runner.errors.report.assert_called_once()
    assert runner.errors.report.call_args.kwargs["operation"] == "run"
    runner.client.get_beta.assert_not_called()


def test_process_company_fetches_only_that_ticker(runner):
    runner.client.get_beta.return_value = Decimal("2.5")

    runner.process_company("NVDA")

    runner.gemini_service.get_companies.assert_not_called()
    runner.client.get_beta.assert_called_once_with("NVDA")
    runner.indicators_service.upsert_beta.assert_called_once_with(
        "NVDA",
        BetaIndicator(value=Decimal("2.5"), retrieved_on=TODAY),
    )


def test_process_company_never_raises(runner):
    runner.client.get_beta.side_effect = RuntimeError("api down")

    runner.process_company("NVDA")

    runner.errors.report_error_message.assert_called_once()
