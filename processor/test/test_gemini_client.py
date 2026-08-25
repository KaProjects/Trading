import json
import logging
from datetime import date
from decimal import Decimal
from unittest.mock import patch

import pytest

from gemini.client import (
    GEMINI_RETRY_ATTEMPTS,
    GEMINI_RETRY_INITIAL_DELAY_SECONDS,
    GEMINI_RETRY_MAX_DELAY_SECONDS,
    GEMINI_RETRYABLE_HTTP_STATUS_CODES,
    GeminiClient,
    InvalidInitialCompanyResponse,
    InvalidQuarterReportResponse,
)
from gemini.models import (
    InitialCompanyResponse,
    Quarter,
    Target,
    TargetReport,
    Targets,
)


def make_quarter(**overrides):
    data = {
        "name": "Q4 2026",
        "id": "26Q4",
        "ending_month": "26-05",
        "report_date_previous_quarter": "2026-04-08",
        "report_date_this_quarter": "2026-07-27",
    }
    data.update(overrides)
    return Quarter(**data)


def complete_quarter_data():
    return {
        "name": "Q4 2026",
        "id": "26Q4",
        "ending_month": "26-05",
        "report_date_previous_quarter": "2026-04-08",
        "report_date_this_quarter": "2026-07-27",
        "reported_eps": "-0.39",
        "reported_revenues": "258.7",
        "reported_gross_profit": "77.1",
        "reported_operating_income": "39.9",
        "reported_net_income": "-110.6",
        "reported_capex": "45.2",
        "reported_free_cash_flow": "-72.4",
        "reported_div": "0",
        "reported_shares": "283.59",
        "price_min": "24.03",
        "price_max": "50.73",
    }


def initial_company_data():
    quarter_dates = {
        "25Q3": ("25-09", "2025-07-16", "2025-10-15"),
        "25Q4": ("25-12", "2025-10-15", "2026-01-28"),
        "26Q1": ("26-03", "2026-01-28", "2026-04-15"),
        "26Q2": ("26-06", "2026-04-15", "2026-07-15"),
        "26Q3": ("26-09", "2026-07-15", "2026-10-14"),
    }
    financial_fields = {
        "reported_eps": "1.25",
        "reported_revenues": "1000",
        "reported_gross_profit": "500",
        "reported_operating_income": "300",
        "reported_net_income": "200",
        "reported_capex": "75",
        "reported_free_cash_flow": "180",
        "reported_div": "0",
        "reported_shares": "100",
        "price_min": "90",
        "price_max": "110",
    }
    quarters = []
    for quarter_id, dates in reversed(quarter_dates.items()):
        quarter = {
            "name": f"Q{quarter_id[-1]} 20{quarter_id[:2]}",
            "id": quarter_id,
            "ending_month": dates[0],
            "report_date_previous_quarter": dates[1],
            "report_date_this_quarter": dates[2],
            **financial_fields,
        }
        if quarter_id == "26Q3":
            quarter.update(dict.fromkeys(financial_fields))
        quarters.append(quarter)

    return {
        "info": {
            "ticker": "ASML",
            "currency": "€",
            "last_update": "2026-08-24",
            "current_quarter_id": "26Q3",
        },
        "quarters": quarters,
        "errors": [],
    }


def test_client_configures_retries_for_transient_http_failures():
    with patch("gemini.client.genai.Client", autospec=True) as client:
        GeminiClient(api_key="gemini-key", model="gemini-model")

    http_options = client.call_args.kwargs["http_options"]
    retry_options = http_options.retry_options
    assert retry_options.attempts == GEMINI_RETRY_ATTEMPTS
    assert (
        retry_options.initial_delay
        == GEMINI_RETRY_INITIAL_DELAY_SECONDS
    )
    assert retry_options.max_delay == GEMINI_RETRY_MAX_DELAY_SECONDS
    assert retry_options.exp_base == 2
    assert retry_options.jitter == 1.0
    assert (
        retry_options.http_status_codes
        == GEMINI_RETRYABLE_HTTP_STATUS_CODES
    )


def test_initial_company_error_contains_exact_gemini_response():
    raw_response = json.dumps({
        "info": {
            "ticker": "ASML",
            "currency": "€",
            "last_update": "2026-08-24",
            "current_quarter_id": "26Q3",
        },
        "quarters": {},
        "errors": [
            "Quarter data could not be retrieved from available sources."
        ],
    })
    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = (
            raw_response
        )
        client = GeminiClient(api_key="gemini-key", model="gemini-model")

        with pytest.raises(InvalidInitialCompanyResponse) as raised:
            client.get_initial_stock_data("ASML")

    message = str(raised.value)
    assert raised.value.raw_response == raw_response
    assert "response validation failed" in message
    assert "GEMINI RESPONSE START" in message
    assert raw_response in message
    assert "GEMINI RESPONSE END" in message
    constructor.return_value.models.generate_content.assert_called_once()


def test_initial_company_accepts_null_financials_with_valid_structure():
    response_data = initial_company_data()
    response_data["quarters"][2]["reported_div"] = None
    response_data["errors"] = [
        "26Q1 reported dividend was unavailable from public sources."
    ]
    raw_response = json.dumps(response_data)
    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = (
            raw_response
        )
        client = GeminiClient(api_key="gemini-key", model="gemini-model")

        result = client.get_initial_stock_data("ASML")

    assert set(result.company.quarters) == {
        quarter["id"] for quarter in response_data["quarters"]
    }
    assert result.company.quarters["26Q1"].reported_div is None
    assert result.company.info.currency == "€"
    assert result.errors == tuple(response_data["errors"])
    prompt = (
        constructor.return_value.models.generate_content.call_args.kwargs[
            "contents"
        ]
    )
    assert "exactly five quarter objects" in prompt
    assert "use null for that field" in prompt
    assert "company's official investor-relations earnings release" in prompt
    assert "detailed financial statements" in prompt
    assert "rounded narrative summaries" in prompt
    assert "A value missing from a summary page is not evidence" in prompt
    assert "reported_capex must represent capital expenditures" in prompt
    assert "reported_free_cash_flow" in prompt
    assert "Never use a year-to-date value as a quarterly value" in prompt


def test_initial_company_rejects_missing_report_date_with_raw_response():
    response_data = initial_company_data()
    response_data["quarters"][-1]["report_date_this_quarter"] = None
    raw_response = json.dumps(response_data)
    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = (
            raw_response
        )
        client = GeminiClient(api_key="gemini-key", model="gemini-model")

        with pytest.raises(InvalidInitialCompanyResponse) as raised:
            client.get_initial_stock_data("ASML")

    assert "response validation failed" in str(raised.value)
    assert raised.value.raw_response == raw_response
    constructor.return_value.models.generate_content.assert_called_once()


def test_initial_company_returns_missing_data_for_currency_policy():
    response_data = initial_company_data()
    response_data["quarters"][1]["reported_revenues"] = None
    raw_response = json.dumps(response_data)
    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = (
            raw_response
        )
        client = GeminiClient(api_key="gemini-key", model="gemini-model")

        result = client.get_initial_stock_data("ASML")

    assert result.company.quarters["26Q2"].reported_revenues is None
    assert result.errors == ()


def test_initial_company_schema_requires_exactly_five_quarters():
    schema = InitialCompanyResponse.model_json_schema()
    quarters_schema = schema["properties"]["quarters"]

    assert quarters_schema["type"] == "array"
    assert quarters_schema["minItems"] == 5
    assert quarters_schema["maxItems"] == 5
    assert "patternProperties" not in json.dumps(schema)


def test_get_price_targets_returns_python_objects_and_uses_targets_schema():
    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = """
        {
          "targets": [
            {
              "ticker": "AAPL",
              "institution": "Important Research",
              "date": "2026-07-15",
              "price": 225.5,
              "rating": "Outperform",
              "source": "https://research.example.com/aapl"
            }
          ]
        }
        """
        client = GeminiClient(api_key="gemini-key", model="gemini-model")

        targets = client.get_price_targets(
            ["AAPL"],
            date(2026, 7, 13),
            date(2026, 7, 19),
        )

    assert targets == Targets(targets=[
        Target(
            ticker="AAPL",
            institution="Important Research",
            date="2026-07-15",
            price=Decimal("225.5"),
            rating="Outperform",
            source="https://research.example.com/aapl",
        ),
    ])
    request = (
        constructor.return_value.models.generate_content.call_args
    )
    assert "2026-07-13 through 2026-07-19" in request.kwargs["contents"]
    assert (
        "Reddit is never an acceptable source of information"
        in request.kwargs["contents"]
    )
    assert request.kwargs["config"]["response_json_schema"] == (
        Targets.model_json_schema()
    )


def test_get_target_report_appends_structured_report_to_original_target():
    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = """
        {
          "overview": "Baird raised its outlook after stronger demand.",
          "key_takeaways": [
            "The new target reflects higher revenue expectations.",
            "The Outperform rating was maintained."
          ]
        }
        """
        client = GeminiClient(api_key="gemini-key", model="gemini-model")
        target = Target(
            ticker="AMD",
            institution="Baird",
            date="2026-07-24",
            price="250",
            rating="Outperform",
            source="https://research.example.com/amd",
        )

        result = client.get_target_report(target)

    report = TargetReport(
        overview="Baird raised its outlook after stronger demand.",
        key_takeaways=[
            "The new target reflects higher revenue expectations.",
            "The Outperform rating was maintained.",
        ],
    )
    assert result == target.model_copy(update={"report": report})
    assert target.report is None
    request = constructor.return_value.models.generate_content.call_args
    assert (
        "Baird recently issued a $250 price target for\n        AMD."
        in request.kwargs["contents"]
    )
    assert "'ticker': 'AMD'" in request.kwargs["contents"]
    assert "between one and four" in request.kwargs["contents"]
    assert "ordered from most\n        to least important" in request.kwargs[
        "contents"
    ]
    assert "1000 characters" in request.kwargs["contents"]
    assert "500 characters" in request.kwargs["contents"]
    assert request.kwargs["config"]["response_json_schema"] == (
        TargetReport.model_json_schema()
    )


def test_get_target_report_truncates_overflow_and_logs_target(caplog):
    response = {
        "overview": "o" * 1005,
        "key_takeaways": [
            "t" * 505,
            "Second",
            "Third",
            "Fourth",
            "Fifth",
            "Sixth",
        ],
    }
    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = (
            json.dumps(response)
        )
        client = GeminiClient(api_key="gemini-key", model="gemini-model")
        target = Target(
            ticker="AMD",
            institution="Baird",
            date="2026-07-24",
            price="250",
            rating="Outperform",
            source="https://research.example.com/amd",
        )

        with caplog.at_level(logging.WARNING, logger="gemini.models"):
            result = client.get_target_report(target)

    assert result.report is not None
    assert len(result.report.overview) == 1000
    assert result.report.overview.endswith("...")
    assert len(result.report.key_takeaways) == 4
    assert len(result.report.key_takeaways[0]) == 500
    assert result.report.key_takeaways[0].endswith("...")
    target_context = "AMD / Baird / 2026-07-24 / $250"
    assert target_context in caplog.text
    assert "actual=1005, limit=1000" in caplog.text
    assert "actual=6, limit=4" in caplog.text
    assert "actual=505, limit=500" in caplog.text


@pytest.mark.parametrize("missing_value", [None, "", "omitted"])
def test_get_quarter_report_returns_incomplete_response_for_runner_policy(
    missing_value,
):
    original = make_quarter()
    response_data = complete_quarter_data()
    if missing_value == "omitted":
        response_data.pop("reported_gross_profit")
    else:
        response_data["reported_gross_profit"] = missing_value

    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = (
            json.dumps(response_data)
        )
        client = GeminiClient(api_key="gemini-key", model="gemini-model")

        result = client.get_quarter_report("APLD", original, "$")

    assert result.quarter.reported_gross_profit is None
    assert result.raw_response == json.dumps(response_data)
    prompt = (
        constructor.return_value.models.generate_content.call_args.kwargs[
            "contents"
        ]
    )
    assert "All reported financial and price fields must be populated" in prompt


def test_get_quarter_report_accepts_complete_response():
    original = make_quarter()
    response_data = complete_quarter_data()

    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = (
            json.dumps(response_data)
        )
        client = GeminiClient(api_key="gemini-key", model="gemini-model")

        result = client.get_quarter_report("APLD", original, "€")

    assert result.quarter != original
    assert result.quarter.reported_gross_profit == Decimal("77.1")
    assert result.quarter.reported_capex == Decimal("45.2")
    assert result.quarter.reported_free_cash_flow == Decimal("-72.4")
    assert result.quarter.reported_div == Decimal("0")
    prompt = (
        constructor.return_value.models.generate_content.call_args.kwargs[
            "contents"
        ]
    )
    assert "reporting currency is €" in prompt
    assert "without converting them to USD" in prompt
    assert "reported_revenues and reported_net_income must be populated" in (
        prompt
    )
    assert "reported_capex must represent capital expenditures" in prompt
    assert "Never use a year-to-date value as a quarterly value" in prompt


def test_get_quarter_report_rejects_missing_structure_with_raw_response():
    original = make_quarter()
    response_data = complete_quarter_data()
    response_data.pop("id")
    raw_response = json.dumps(response_data)

    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = (
            raw_response
        )
        client = GeminiClient(api_key="gemini-key", model="gemini-model")

        with pytest.raises(InvalidQuarterReportResponse) as raised:
            client.get_quarter_report("APLD", original, "€")

    assert raised.value.raw_response == raw_response
    assert "Invalid quarter report response for APLD 26Q4" in str(
        raised.value
    )
    assert raw_response in str(raised.value)
