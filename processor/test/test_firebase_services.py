import logging
from datetime import datetime
from unittest.mock import MagicMock, create_autospec, patch

import pytest

from gemini.models import Quarter, ReportDate
from gemini.service import FirebaseService as GeminiFirebaseService
from gemini.service import company_path as gemini_company_path
from myfinnhub.service import FirebaseService as FinnhubFirebaseService


class FakeReference:
    def __init__(self, value):
        self.value = value

    def get(self):
        return self.value


def make_service(service_class):
    service = object.__new__(service_class)
    service.log = create_autospec(logging.Logger, instance=True)
    return service


def test_gemini_service_treats_empty_snapshot_as_empty_mapping():
    with patch("gemini.service.db.reference", autospec=True, return_value=FakeReference(None)):
        assert make_service(GeminiFirebaseService).get_companies() == {}


def test_finnhub_service_treats_empty_snapshot_as_empty_mapping():
    with patch("myfinnhub.service.db.reference", autospec=True, return_value=FakeReference(None)):
        assert make_service(FinnhubFirebaseService).get_companies() == {}


@pytest.mark.parametrize(
    ("service_class", "reference_path", "data_root"),
    [
        (GeminiFirebaseService, "gemini.service.db.reference", "gemini"),
        (FinnhubFirebaseService, "myfinnhub.service.db.reference", "fhe"),
    ],
)
def test_firebase_service_skips_malformed_company_records(
    service_class,
    reference_path,
    data_root,
):
    snapshot = {
        "AAPL": None,
        "MSFT": "invalid",
        "NVDA": {data_root: None},
    }
    service = make_service(service_class)
    with patch(reference_path, autospec=True, return_value=FakeReference(snapshot)):
        companies = service.get_companies()

    assert companies == {"NVDA": None}
    assert service.log.error.call_count == 2


@pytest.mark.parametrize(
    ("service_class", "reference_path"),
    [
        (GeminiFirebaseService, "gemini.service.db.reference"),
        (FinnhubFirebaseService, "myfinnhub.service.db.reference"),
    ],
)
def test_firebase_service_rejects_non_mapping_root(service_class, reference_path):
    with patch(reference_path, autospec=True, return_value=FakeReference("invalid")):
        with pytest.raises(TypeError, match="must be a mapping"):
            make_service(service_class).get_companies()


def test_gemini_report_date_update_is_atomic():
    service = make_service(GeminiFirebaseService)
    company_reference = MagicMock(spec_set=["child", "update"])
    report_date_reference = MagicMock(spec_set=["get"])
    report_date_reference.get.return_value = "2026-05-01"
    company_reference.child.return_value = report_date_reference
    report_date = ReportDate(
        ticker="AAPL",
        quarter="26Q1",
        report_date="2026-05-03",
    )

    with (
        patch("gemini.service.db.reference", return_value=company_reference) as reference,
        patch("gemini.service.datetime") as current_datetime,
    ):
        current_datetime.now.return_value = datetime(2026, 4, 27)
        service.update_report_date(report_date)

    reference.assert_called_once_with(gemini_company_path("AAPL"))
    company_reference.child.assert_called_once_with(
        "quarters/26Q1/report_date_this_quarter"
    )
    company_reference.update.assert_called_once_with({
        "quarters/26Q1/report_date_this_quarter": "2026-05-03",
        "info/last_update": "2026-04-27",
    })


@pytest.mark.parametrize(
    ("method_name", "updates_current_quarter"),
    [
        ("report_quarter", False),
        ("create_quarter", True),
    ],
)
def test_gemini_quarter_writes_are_atomic(method_name, updates_current_quarter):
    service = make_service(GeminiFirebaseService)
    company_reference = MagicMock(spec_set=["update"])
    quarter = Quarter(
        name="Q1 2026",
        id="26Q1",
        ending_month="26-03",
        report_date_previous_quarter="2026-01-20",
        report_date_this_quarter="2026-04-27",
    )

    with (
        patch("gemini.service.db.reference", return_value=company_reference) as reference,
        patch("gemini.service.datetime") as current_datetime,
    ):
        current_datetime.now.return_value = datetime(2026, 4, 28)
        getattr(service, method_name)("AAPL", quarter)

    reference.assert_called_once_with(gemini_company_path("AAPL"))
    expected_update = {
        "quarters/26Q1": quarter.model_dump(),
        "info/last_update": "2026-04-28",
    }
    if updates_current_quarter:
        expected_update["info/current_quarter_id"] = "26Q1"
    company_reference.update.assert_called_once_with(expected_update)
