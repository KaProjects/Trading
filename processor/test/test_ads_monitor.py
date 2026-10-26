import logging
from datetime import date
from decimal import Decimal
from unittest.mock import create_autospec

import pytest

from error_reporting import ErrorReporter
from gemini.ads_monitor import AdsRatioMonitorRunner
from gemini.adr import AdsRatio, AdsRatioDetector
from gemini.models import Company, Info
from gemini.service import FirebaseService as GeminiFirebaseService

TODAY = date(2026, 11, 1)


def make_company(ticker, ratio):
    return Company(
        info=Info(
            ticker=ticker,
            last_update="2026-10-01",
            current_quarter_id="26Q3",
            ads_per_share=ratio,
        ),
        quarters={},
    )


def detected(ratio):
    return AdsRatio(
        ads_per_share=Decimal(ratio),
        ads_shares=Decimal(0),
        ordinary_shares=Decimal(0),
    )


@pytest.fixture
def runner():
    instance = AdsRatioMonitorRunner(
        create_autospec(AdsRatioDetector, instance=True),
        gemini_service=create_autospec(GeminiFirebaseService, instance=True),
        error_reporter=create_autospec(ErrorReporter, instance=True),
        clock=lambda: TODAY,
    )
    instance.log = create_autospec(logging.Logger, instance=True)
    return instance


def test_a_confirmed_ratio_only_records_the_check(runner):
    runner.gemini_service.get_companies.return_value = {
        "SKHY": make_company("SKHY", Decimal("10")),
    }
    runner.detector.detect.return_value = detected("10")

    runner.run()

    runner.gemini_service.update_ads_check.assert_called_once_with(
        "SKHY", TODAY
    )
    runner.errors.report_warning_message.assert_not_called()


def test_only_companies_with_a_ratio_are_checked(runner):
    runner.gemini_service.get_companies.return_value = {
        "MSFT": make_company("MSFT", None),
        "PENDING": None,
        "SKHY": make_company("SKHY", Decimal("10")),
    }
    runner.detector.detect.return_value = detected("10")

    runner.run()

    runner.detector.detect.assert_called_once_with("SKHY")


def test_a_changed_ratio_is_a_warning_and_is_not_applied(runner):
    runner.gemini_service.get_companies.return_value = {
        "SKHY": make_company("SKHY", Decimal("10")),
    }
    runner.detector.detect.return_value = detected("5")

    runner.run()

    runner.gemini_service.update_ads_check.assert_not_called()
    message = runner.errors.report_warning_message.call_args.args[0]
    assert "SKHY" in message and "5 ADSs per share" in message
    assert "stored 10" in message


def test_an_unconfirmable_ratio_is_a_warning(runner):
    runner.gemini_service.get_companies.return_value = {
        "SKHY": make_company("SKHY", Decimal("10")),
    }
    runner.detector.detect.return_value = None

    runner.run()

    message = runner.errors.report_warning_message.call_args.args[0]
    assert "could not be confirmed" in message
    runner.gemini_service.update_ads_check.assert_not_called()


def test_problems_are_one_message_and_one_failure_does_not_stop_the_rest(
    runner,
):
    runner.gemini_service.get_companies.return_value = {
        "AAA": make_company("AAA", Decimal("2")),
        "BBB": make_company("BBB", Decimal("10")),
        "CCC": make_company("CCC", Decimal("5")),
    }
    runner.detector.detect.side_effect = [
        RuntimeError("429"),
        detected("10"),
        detected("4"),
    ]

    runner.run()

    runner.errors.report_warning_message.assert_called_once()
    message = runner.errors.report_warning_message.call_args.args[0]
    assert "AAA: check failed (429)" in message
    assert "CCC" in message
    runner.gemini_service.update_ads_check.assert_called_once_with(
        "BBB", TODAY
    )


def test_an_unreadable_company_list_is_reported_not_raised(runner):
    runner.gemini_service.get_companies.side_effect = RuntimeError("db down")

    runner.run()

    runner.errors.report.assert_called_once()
    runner.detector.detect.assert_not_called()
