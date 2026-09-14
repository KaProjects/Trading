import logging
from types import SimpleNamespace
from unittest.mock import create_autospec, patch

from company_onboarding import CompanyOnboardingWatcher
from error_reporting import ErrorReporter
from gemini.retriever import StockDataRetrieverRunner
from myfinnhub.retriever import FinnhubEarningsRetrieverRunner
from polygon.retriever import PolygonNewsRetrieverRunner


def make_event(path: str, data: object, event_type: str = "put"):
    return SimpleNamespace(path=path, data=data, event_type=event_type)


def make_watcher():
    watcher = CompanyOnboardingWatcher(
        gemini=create_autospec(StockDataRetrieverRunner, instance=True),
        finnhub=create_autospec(
            FinnhubEarningsRetrieverRunner,
            instance=True,
        ),
        polygon=create_autospec(PolygonNewsRetrieverRunner, instance=True),
        error_reporter=create_autospec(ErrorReporter, instance=True),
    )
    watcher.log = create_autospec(logging.Logger, instance=True)
    return watcher


def test_initial_snapshot_seeds_known_keys_without_onboarding():
    watcher = make_watcher()

    watcher._on_event(make_event("/", {
        "AAPL": {"gemini": {"info": {}}},
        "MSFT": "",
    }))

    watcher.gemini.onboard_company.assert_not_called()
    assert watcher._known_company_keys == {"AAPL", "MSFT"}


def test_new_placeholder_company_triggers_full_onboarding_chain():
    watcher = make_watcher()
    company = object()
    watcher.gemini.onboard_company.return_value = company

    watcher._on_event(make_event("/NVDA", ""))

    watcher.gemini.onboard_company.assert_called_once_with("NVDA")
    watcher.finnhub.process_company.assert_called_once_with("NVDA", None)
    watcher.polygon.process_company.assert_called_once_with("NVDA")


def test_dotted_ticker_is_translated_from_its_firebase_key():
    watcher = make_watcher()
    watcher.gemini.onboard_company.return_value = None

    watcher._on_event(make_event("/BRK-B", ""))

    watcher.gemini.onboard_company.assert_called_once_with("BRK.B")


def test_failed_gemini_initialization_stops_the_pipeline():
    watcher = make_watcher()
    watcher.gemini.onboard_company.return_value = None

    watcher._on_event(make_event("/NVDA", ""))

    watcher.gemini.onboard_company.assert_called_once_with("NVDA")
    watcher.finnhub.process_company.assert_not_called()
    watcher.polygon.process_company.assert_not_called()


def test_edit_to_an_already_known_company_does_not_trigger_onboarding():
    watcher = make_watcher()
    watcher._on_event(make_event("/", {"AAPL": {"gemini": {"info": {}}}}))

    watcher._on_event(make_event(
        "/AAPL/gemini/quarters/26Q4",
        {"ending_month": "26-09"},
        event_type="patch",
    ))

    watcher.gemini.onboard_company.assert_not_called()


def test_same_new_ticker_is_only_onboarded_once():
    watcher = make_watcher()
    watcher.gemini.onboard_company.return_value = None

    watcher._on_event(make_event("/NVDA", ""))
    watcher._on_event(make_event(
        "/NVDA/gemini/info",
        {"ticker": "NVDA"},
        event_type="patch",
    ))

    watcher.gemini.onboard_company.assert_called_once_with("NVDA")


def test_removed_company_can_be_re_added_and_retried():
    watcher = make_watcher()
    watcher.gemini.onboard_company.return_value = None

    watcher._on_event(make_event("/NVDA", ""))
    watcher._on_event(make_event("/NVDA", None))
    watcher._on_event(make_event("/NVDA", ""))

    assert watcher.gemini.onboard_company.call_count == 2


def test_nested_write_for_a_never_seen_ticker_does_not_onboard():
    watcher = make_watcher()

    watcher._on_event(make_event(
        "/NVDA/gemini/info",
        {"ticker": "NVDA"},
        event_type="patch",
    ))

    watcher.gemini.onboard_company.assert_not_called()
    assert "NVDA" in watcher._known_company_keys


def test_exception_in_handler_is_reported_and_does_not_propagate():
    watcher = make_watcher()
    error = RuntimeError("Gemini unavailable")
    watcher.gemini.onboard_company.side_effect = error

    watcher._on_event(make_event("/NVDA", ""))

    watcher.errors.report.assert_called_once_with(
        error,
        logger=watcher.log,
        source=watcher.name,
        operation="handle_company_event",
        context={"path": "/NVDA", "event_type": "put"},
    )


def test_start_registers_listener_on_company_reference():
    watcher = make_watcher()
    with patch("company_onboarding.db") as db_module:
        registration = object()
        db_module.reference.return_value.listen.return_value = registration

        result = watcher.start()

    db_module.reference.assert_called_once_with("company")
    db_module.reference.return_value.listen.assert_called_once_with(
        watcher._on_event
    )
    assert result is registration
