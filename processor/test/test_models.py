import pytest
from pydantic import ValidationError

from gemini.models import Company, Info, Quarter, ReportDate, ReportDates


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

    assert Company.model_validate(company.model_dump()) == company
    assert ReportDates.model_validate(report_dates.model_dump()) == report_dates
