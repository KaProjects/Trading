import logging
from datetime import datetime
from unittest.mock import MagicMock, create_autospec, patch

import pytest

from error_reporting import ErrorReporter
from gemini.models import (
    CompanyTarget,
    InstitutionRecord,
    Quarter,
    ReportDate,
)
from gemini.service import FirebaseService as GeminiFirebaseService
from gemini.service import company_path as gemini_company_path
from gemini.service import create_target_id
from gemini.service import institutions_path
from myfinnhub.service import FirebaseService as FinnhubFirebaseService


class FakeReference:
    def __init__(self, value):
        self.value = value

    def get(self):
        return self.value


def make_service(service_class):
    service = object.__new__(service_class)
    service.log = create_autospec(logging.Logger, instance=True)
    service.errors = create_autospec(ErrorReporter, instance=True)
    return service


def test_gemini_service_treats_empty_snapshot_as_empty_mapping():
    with patch("gemini.service.db.reference", autospec=True, return_value=FakeReference(None)):
        assert make_service(GeminiFirebaseService).get_companies() == {}


def test_finnhub_service_treats_empty_snapshot_as_empty_mapping():
    with patch("myfinnhub.service.db.reference", autospec=True, return_value=FakeReference(None)):
        assert make_service(FinnhubFirebaseService).get_companies() == {}


def test_gemini_service_loads_institutions():
    service = make_service(GeminiFirebaseService)
    institution = InstitutionRecord(
        name="Baird",
        aliases={
            "baird": "Baird",
            "robert-w-baird": "Robert W. Baird",
        },
        enabled=True,
    )

    with patch(
        "gemini.service.db.reference",
        autospec=True,
        return_value=FakeReference({
            "baird": institution.model_dump(mode="json"),
        }),
    ) as reference:
        institutions = service.get_institutions()

    reference.assert_called_once_with(institutions_path)
    assert institutions == {"baird": institution}


def test_gemini_service_batch_creates_institutions():
    service = make_service(GeminiFirebaseService)
    institution_reference = MagicMock(spec_set=["update"])
    institutions = {
        "northland-securities": InstitutionRecord(
            name="Northland Securities",
            aliases={
                "northland-securities": "Northland Securities",
            },
            enabled=True,
        ),
    }

    with patch(
        "gemini.service.db.reference",
        autospec=True,
        return_value=institution_reference,
    ) as reference:
        service.create_institutions(institutions)

    reference.assert_called_once_with(institutions_path)
    institution_reference.update.assert_called_once_with({
        "northland-securities": (
            institutions["northland-securities"].model_dump(mode="json")
        ),
    })
    service.log.info.assert_called_once_with(
        "Created %d institutions: %s",
        1,
        "northland-securities",
    )


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


def test_firebase_validation_error_is_reported_with_company_context():
    service = make_service(GeminiFirebaseService)
    snapshot = {"AAPL": {"gemini": {}}}

    with patch(
        "gemini.service.db.reference",
        autospec=True,
        return_value=FakeReference(snapshot),
    ):
        companies = service.get_companies()

    assert companies == {}
    error = service.errors.report.call_args.args[0]
    service.errors.report.assert_called_once_with(
        error,
        logger=service.log,
        source="FirebaseRepository",
        operation="parse_company",
        context={"company_id": "AAPL", "data_root": "gemini"},
    )


def test_gemini_service_parses_targets_inside_company():
    service = make_service(GeminiFirebaseService)
    target_id = "2026-07-15-a1b2c3"
    snapshot = {
        "AAPL": {
            "gemini": {
                "info": {
                    "ticker": "AAPL",
                    "last_update": "2026-07-20",
                    "current_quarter_id": "26Q2",
                },
                "quarters": {},
                "targets": {
                    target_id: {
                        "institution": "Important Research",
                        "date": "2026-07-15",
                        "price": "225.50",
                        "rating": "Outperform",
                        "source": "https://research.example.com/aapl",
                    },
                },
            },
        },
    }

    with patch(
        "gemini.service.db.reference",
        autospec=True,
        return_value=FakeReference(snapshot),
    ):
        companies = service.get_companies()

    assert companies["AAPL"].targets[target_id] == CompanyTarget(
        institution="Important Research",
        date="2026-07-15",
        price="225.50",
        rating="Outperform",
        source="https://research.example.com/aapl",
    )


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
        "quarters/26Q1": quarter.model_dump(mode="json"),
        "info/last_update": "2026-04-28",
    }
    if updates_current_quarter:
        expected_update["info/current_quarter_id"] = "26Q1"
    company_reference.update.assert_called_once_with(expected_update)


def test_gemini_target_write_uses_stable_date_prefixed_id():
    service = make_service(GeminiFirebaseService)
    target_reference = MagicMock(spec_set=["set"])
    target = CompanyTarget(
        institution="Important Research",
        date="2026-07-15",
        price="225.50",
        rating="Outperform",
        source="https://research.example.com/aapl?tracking=1",
    )
    target_id = create_target_id("AAPL", target)

    with patch(
        "gemini.service.db.reference",
        autospec=True,
        return_value=target_reference,
    ) as reference:
        result = service.upsert_target("AAPL", target)

    assert result == target_id
    assert target_id.startswith("2026-07-15-")
    reference.assert_called_once_with(
        f"{gemini_company_path('AAPL')}/targets/{target_id}"
    )
    target_reference.set.assert_called_once_with(
        target.model_dump(mode="json")
    )
