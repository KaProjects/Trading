import logging
from datetime import date
from decimal import Decimal

import pytest
from pydantic import ValidationError

from gemini.models import (
    Company,
    CompanyTarget,
    Info,
    Quarter,
    ReportDate,
    ReportDates,
    Target,
    TargetReport,
    Targets,
)
from myfinnhub.models import Company as FinnhubCompany
from myfinnhub.models import Earnings


def test_gemini_company_rejects_missing_required_info():
    with pytest.raises(ValidationError):
        Company.model_validate({"quarters": {}})


def test_gemini_company_serialization_round_trip():
    quarter = Quarter(
        id="26Q1",
        name="Q1 2026",
        ending_month="26-03",
        report_date_previous_quarter="2026-01-20",
        report_date_this_quarter="2026-04-27",
        reported_eps="1.25",
        reported_revenues="",
    )
    company = Company(
        info=Info(
            ticker="AAPL",
            last_update="2026-04-27",
            current_quarter_id="26Q1",
        ),
        quarters={"26Q1": quarter},
    )
    report_dates = ReportDates(report_dates=[
        ReportDate(ticker="AAPL", quarter="26Q1", report_date="2026-04-27")
    ])
    target = CompanyTarget(
        institution="Important Research",
        date="2026-04-20",
        price="225.50",
        rating="Outperform",
        source="https://research.example.com/aapl",
    )
    company.targets["2026-04-20-a1b2c3"] = target

    assert quarter.report_date_this_quarter == date(2026, 4, 27)
    assert quarter.reported_eps == Decimal("1.25")
    assert quarter.reported_revenues is None
    assert company.targets["2026-04-20-a1b2c3"] == target
    assert Company.model_validate(company.model_dump(mode="json")) == company
    assert ReportDates.model_validate(
        report_dates.model_dump(mode="json")
    ) == report_dates


@pytest.mark.parametrize(
    "invalid_quarter_id",
    ["Q1", "2026Q1", "26Q5", "invalid"],
)
def test_quarter_rejects_invalid_identifier(invalid_quarter_id):
    with pytest.raises(ValidationError):
        Quarter(
            id=invalid_quarter_id,
            name="invalid",
            ending_month="26-03",
            report_date_previous_quarter="2026-01-20",
        )


def test_company_rejects_quarter_key_that_does_not_match_model_id():
    quarter = Quarter(
        id="26Q1",
        name="Q1 2026",
        ending_month="26-03",
        report_date_previous_quarter="2026-01-20",
    )

    with pytest.raises(ValidationError, match="Quarter keys do not match"):
        Company(
            info=Info(
                ticker="AAPL",
                last_update="2026-04-27",
                current_quarter_id="26Q1",
            ),
            quarters={"26Q2": quarter},
        )


def test_report_dates_reject_duplicate_company_quarter_identity():
    with pytest.raises(ValidationError, match="identities must be unique"):
        ReportDates(report_dates=[
            ReportDate(
                ticker="AAPL",
                quarter="26Q1",
                report_date="2026-04-27",
            ),
            ReportDate(
                ticker="AAPL",
                quarter="26Q1",
                report_date="2026-04-28",
            ),
        ])


def test_target_schema_describes_every_output_field():
    target_schema = Target.model_json_schema()
    for field_name in (
        "ticker",
        "institution",
        "date",
        "price",
        "rating",
        "source",
    ):
        assert target_schema["properties"][field_name]["description"]
    assert target_schema["properties"]["source"]["maxLength"] == 1024

    targets_schema = Targets.model_json_schema()
    assert targets_schema["properties"]["targets"]["description"]

    report_schema = TargetReport.model_json_schema()
    assert report_schema["properties"]["overview"]["description"]
    assert report_schema["properties"]["key_takeaways"]["description"]
    assert report_schema["properties"]["overview"]["maxLength"] == 1000
    assert report_schema["properties"]["key_takeaways"]["maxItems"] == 4
    assert (
        report_schema["properties"]["key_takeaways"]["items"]["maxLength"]
        == 500
    )


def test_target_report_normalizes_oversized_gemini_response(caplog):
    overview = "o" * 1005
    takeaways = ["t" * 505] + [
        f"Takeaway {index}" for index in range(2, 7)
    ]
    with caplog.at_level(logging.WARNING, logger="gemini.models"):
        report = TargetReport(
            overview=overview,
            key_takeaways=takeaways,
        )

    assert len(report.overview) == 1000
    assert report.overview.endswith("...")
    assert len(report.key_takeaways) == 4
    assert len(report.key_takeaways[0]) == 500
    assert report.key_takeaways[0].endswith("...")
    assert "actual=1005, limit=1000" in caplog.text
    assert "actual=6, limit=4" in caplog.text
    assert "actual=505, limit=500" in caplog.text


def test_quarter_schema_describes_every_output_field_and_unit():
    properties = Quarter.model_json_schema()["properties"]

    assert all(
        properties[field_name]["description"]
        for field_name in Quarter.model_fields
    )
    for field_name in (
        "reported_revenues",
        "reported_gross_profit",
        "reported_operating_income",
        "reported_net_income",
        "reported_div",
    ):
        assert "millions of USD" in properties[field_name]["description"]
    assert (
        "millions of shares"
        in properties["reported_shares"]["description"]
    )
    for field_name in ("reported_eps", "price_min", "price_max"):
        assert "USD per share" in properties[field_name]["description"]


def test_finnhub_models_parse_legacy_numbers_and_validate_keys():
    earnings = Earnings(
        report="2026-04-27-bmo",
        epse=1.25,
        epsa="",
        reve="1000000",
    )
    company = FinnhubCompany.model_validate({
        "26Q1": {
            "20260427": earnings.model_dump(mode="json"),
        }
    })

    assert earnings.epse == Decimal("1.25")
    assert earnings.epsa is None
    assert earnings.reve == Decimal("1000000")
    assert company.model_dump(mode="json")["26Q1"]["20260427"]["epse"] == "1.25"


def test_finnhub_models_reject_invalid_report_and_quarter():
    with pytest.raises(ValidationError):
        Earnings(report="not-a-date")

    with pytest.raises(ValidationError):
        FinnhubCompany.model_validate({"Q1": {}})


@pytest.mark.parametrize("non_finite", ["NaN", "Infinity", "-Infinity"])
def test_finnhub_earnings_reject_non_finite_numbers(non_finite):
    with pytest.raises(ValidationError):
        Earnings(report="2026-04-27-bmo", epse=non_finite)
