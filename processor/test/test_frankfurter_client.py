from datetime import date
from decimal import Decimal
from types import SimpleNamespace
from unittest.mock import Mock, create_autospec

import pytest
from requests import Session
from requests.exceptions import Timeout

from frankfurter.client import FrankfurterClient, FrankfurterClientError


@pytest.fixture
def client():
    session = Session()
    session.get = create_autospec(session.get)
    yield FrankfurterClient(session=session, timeout=4.0), session
    session.close()


def response(payload, status_code=200):
    return SimpleNamespace(
        status_code=status_code,
        text="response body",
        json=Mock(return_value=payload),
    )


def test_daily_closes_are_requested_and_returned_oldest_first(client):
    instance, session = client
    session.get.return_value = response({
        "base": "USD",
        "rates": {
            "2026-04-02": {"KRW": 1516.65},
            "2026-04-01": {"KRW": 1504.81},
        },
    })

    closes = instance.get_forex_daily_closes(
        "USD", "KRW", date(2026, 4, 1), date(2026, 6, 30)
    )

    session.get.assert_called_once_with(
        "https://api.frankfurter.dev/v1/2026-04-01..2026-06-30",
        params={"base": "USD", "symbols": "KRW"},
        timeout=4.0,
    )
    assert closes == [Decimal("1504.81"), Decimal("1516.65")]


def test_a_range_without_rates_is_empty(client):
    instance, session = client
    session.get.return_value = response({"base": "USD", "rates": {}})

    assert instance.get_forex_daily_closes(
        "USD", "KRW", date(2026, 4, 4), date(2026, 4, 5)
    ) == []


def test_a_network_failure_raises_a_typed_error(client):
    instance, session = client
    session.get.side_effect = Timeout("slow")

    with pytest.raises(FrankfurterClientError, match="request failed"):
        instance.get_forex_daily_closes(
            "USD", "KRW", date(2026, 4, 1), date(2026, 6, 30)
        )


def test_an_unsuccessful_status_raises_a_typed_error(client):
    instance, session = client
    session.get.return_value = response({"message": "not found"}, 404)

    with pytest.raises(FrankfurterClientError, match="returned 404"):
        instance.get_forex_daily_closes(
            "USD", "XXX", date(2026, 4, 1), date(2026, 6, 30)
        )


@pytest.mark.parametrize(
    "payload",
    [
        {"unexpected": True},
        {"rates": {"2026-04-01": {"EUR": 1.0}}},
        {"rates": {"2026-04-01": {"KRW": 0}}},
        {"rates": {"2026-04-01": {"KRW": "not-a-number"}}},
    ],
)
def test_an_invalid_payload_raises_a_typed_error(client, payload):
    instance, session = client
    session.get.return_value = response(payload)

    with pytest.raises(FrankfurterClientError, match="invalid response"):
        instance.get_forex_daily_closes(
            "USD", "KRW", date(2026, 4, 1), date(2026, 6, 30)
        )
