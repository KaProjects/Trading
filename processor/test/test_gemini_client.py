from unittest.mock import patch

from gemini.client import (
    GEMINI_RETRY_ATTEMPTS,
    GEMINI_RETRY_INITIAL_DELAY_SECONDS,
    GEMINI_RETRY_MAX_DELAY_SECONDS,
    GEMINI_RETRYABLE_HTTP_STATUS_CODES,
    GeminiClient,
)


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
