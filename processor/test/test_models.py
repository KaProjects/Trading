import pytest
from pydantic import ValidationError

from classes import Company as LegacyCompany
from classes import Signal
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


def test_legacy_company_rejects_non_numeric_financial_input():
    with pytest.raises(ValueError):
        LegacyCompany({
            "ticker": "$AAPL",
            "time": "2026-04-27T08:30:00Z",
            "price": "not-a-number",
            "cci": "1",
            "diff": "1",
            "macd": "1",
            "signal": "1",
        })


@pytest.mark.xfail(strict=True, reason="Legacy model __repr__ methods return dictionaries")
def test_legacy_company_repr_returns_a_string():
    company = LegacyCompany({
        "ticker": "$AAPL",
        "time": "2026-04-27T08:30:00Z",
        "price": "150",
        "cci": "1",
        "diff": "1",
        "macd": "1",
        "signal": "1",
    })

    assert isinstance(repr(company), str)


@pytest.mark.xfail(strict=True, reason="Signal accepts values shorter than its three-bit contract")
def test_signal_rejects_incomplete_values():
    with pytest.raises(ValueError):
        Signal("1")
