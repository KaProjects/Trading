from types import SimpleNamespace
from unittest.mock import Mock, call, create_autospec

import pytest
from requests import Session
from requests.exceptions import Timeout

from sec.client import (
    MAX_RETRIES,
    MIN_REQUEST_INTERVAL_SECONDS,
    RETRY_DELAY_SECONDS,
    SecClient,
    SecClientError,
)

TICKERS = {
    "0": {"cik_str": 789019, "ticker": "MSFT", "title": "MICROSOFT CORP"},
    "1": {"cik_str": 1067983, "ticker": "BRK-B", "title": "BERKSHIRE"},
}


def response(payload, status_code=200):
    return SimpleNamespace(
        status_code=status_code,
        text="response body",
        json=Mock(return_value=payload),
    )


@pytest.fixture
def client():
    session = Session()
    session.get = create_autospec(session.get)
    sleeper = Mock()
    now = [0.0]
    instance = SecClient(
        "sec@example.com",
        session=session,
        sleeper=sleeper,
        clock=lambda: now[0],
    )
    yield instance, session, sleeper, now
    session.close()


def test_every_request_identifies_the_caller(client):
    instance, session, *_ = client

    assert session.headers["User-Agent"] == (
        "TradingProcessor sec@example.com"
    )


def test_get_cik_is_zero_padded(client):
    instance, session, *_ = client
    session.get.return_value = response(TICKERS)

    assert instance.get_cik("MSFT") == "0000789019"
    assert session.get.call_args.args[0] == SecClient.TICKERS_URL


def test_get_cik_finds_share_classes_written_with_a_dot(client):
    instance, session, *_ = client
    session.get.return_value = response(TICKERS)

    assert instance.get_cik("BRK.B") == "0001067983"


def test_get_cik_is_none_for_an_unknown_ticker(client):
    instance, session, *_ = client
    session.get.return_value = response(TICKERS)

    assert instance.get_cik("NOPE") is None


def test_get_cik_rejects_an_unexpected_ticker_list(client):
    instance, session, *_ = client
    session.get.return_value = response({"0": {"unexpected": True}})

    with pytest.raises(SecClientError, match="invalid ticker list"):
        instance.get_cik("MSFT")


def test_company_facts_are_requested_by_padded_cik(client):
    instance, session, *_ = client
    payload = {"facts": {"us-gaap": {}}}
    session.get.return_value = response(payload)

    assert instance.get_company_facts("789019") == payload
    assert session.get.call_args.args[0] == (
        "https://data.sec.gov/api/xbrl/companyfacts/CIK0000789019.json"
    )


def test_company_facts_without_facts_are_rejected(client):
    instance, session, *_ = client
    session.get.return_value = response({"cik": 1})

    with pytest.raises(SecClientError, match="invalid company facts"):
        instance.get_company_facts("0000789019")


def test_rate_limited_requests_are_retried_after_a_pause(client):
    instance, session, sleeper, _ = client
    session.get.side_effect = [
        response({}, 429),
        response({"facts": {}}),
    ]

    instance.get_company_facts("0000789019")

    assert call(RETRY_DELAY_SECONDS) in sleeper.call_args_list
    assert session.get.call_count == 2


def test_persistent_failure_raises_after_the_retries(client):
    instance, session, *_ = client
    session.get.return_value = response({}, 503)

    with pytest.raises(SecClientError, match="returned 503"):
        instance.get_company_facts("0000789019")

    assert session.get.call_count == MAX_RETRIES + 1


def test_a_forbidden_response_is_not_retried(client):
    instance, session, *_ = client
    session.get.return_value = response({}, 403)

    with pytest.raises(SecClientError, match="returned 403"):
        instance.get_company_facts("0000789019")

    assert session.get.call_count == 1


def test_network_failures_raise_a_typed_error(client):
    instance, session, *_ = client
    session.get.side_effect = Timeout("slow")

    with pytest.raises(SecClientError, match="request failed"):
        instance.get_company_facts("0000789019")


def test_requests_are_spaced_below_the_published_limit(client):
    instance, session, sleeper, now = client
    session.get.return_value = response({"facts": {}})

    instance.get_company_facts("0000000001")
    now[0] += 0.05
    instance.get_company_facts("0000000002")

    waited = sleeper.call_args_list[0].args[0]
    assert waited == pytest.approx(MIN_REQUEST_INTERVAL_SECONDS - 0.05)


def test_the_ticker_list_is_fetched_once_per_client(client):
    instance, session, *_ = client
    session.get.return_value = response(TICKERS)

    instance.get_cik("MSFT")
    instance.get_cik("BRK.B")
    instance.get_cik("NOPE")

    assert session.get.call_count == 1
