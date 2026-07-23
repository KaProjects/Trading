from datetime import date
from decimal import Decimal
from unittest.mock import patch

from gemini.client import (
    GEMINI_RETRY_ATTEMPTS,
    GEMINI_RETRY_INITIAL_DELAY_SECONDS,
    GEMINI_RETRY_MAX_DELAY_SECONDS,
    GEMINI_RETRYABLE_HTTP_STATUS_CODES,
    GeminiClient,
)
from gemini.models import Target, Targets


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
    assert request.kwargs["config"]["response_json_schema"] == (
        Targets.model_json_schema()
    )
