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
)
from gemini.models import Quarter, Target, TargetReport, Targets


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
        "reported_div": "0",
        "reported_shares": "283.59",
        "price_min": "24.03",
        "price_max": "50.73",
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
def test_get_quarter_report_rejects_incomplete_response(missing_value):
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

        result = client.get_quarter_report("APLD", original)

    assert result is original
    prompt = (
        constructor.return_value.models.generate_content.call_args.kwargs[
            "contents"
        ]
    )
    assert "return the original unfilled data template unchanged" in prompt


def test_get_quarter_report_accepts_complete_response():
    original = make_quarter()
    response_data = complete_quarter_data()

    with patch("gemini.client.genai.Client", autospec=True) as constructor:
        constructor.return_value.models.generate_content.return_value.text = (
            json.dumps(response_data)
        )
        client = GeminiClient(api_key="gemini-key", model="gemini-model")

        result = client.get_quarter_report("APLD", original)

    assert result != original
    assert result.reported_gross_profit == Decimal("77.1")
    assert result.reported_div == Decimal("0")
