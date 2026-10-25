from types import SimpleNamespace
from unittest.mock import Mock, create_autospec

import pytest
from requests import Session
from requests.exceptions import Timeout

from cmc.client import CoinMarketCapClient, CoinMarketCapError
from cmc.models import FearAndGreedClassification


@pytest.fixture
def client():
    session = Session()
    session.get = create_autospec(session.get)
    instance = CoinMarketCapClient(
        api_key="cmc-key",
        session=session,
        timeout=4.0,
    )
    yield instance, session
    session.close()


def response(payload, status_code=200):
    return SimpleNamespace(
        status_code=status_code,
        text="response body",
        json=Mock(return_value=payload),
    )


def test_get_fear_and_greed_reuses_session_and_validates_response(client):
    instance, session = client
    session.get.return_value = response({
        "data": {
            "value": "42",
            "value_classification": "Fear",
        },
    })

    result = instance.get_fear_and_greed()

    assert result.value == 42
    assert result.classification is FearAndGreedClassification.FEAR
    assert session.headers["X-CMC_PRO_API_KEY"] == "cmc-key"
    session.get.assert_called_once_with(
        "https://pro-api.coinmarketcap.com/v3/fear-and-greed/latest",
        params={},
        timeout=4.0,
    )


def test_get_btc_price_extracts_nested_quote(client):
    instance, session = client
    session.get.return_value = response({
        "data": {
            "BTC": [{
                "quote": {
                    "USD": {"price": 50_000},
                },
            }],
        },
    })

    result = instance.get_btc_price()

    assert result.price == 50_000
    session.get.assert_called_once_with(
        "https://pro-api.coinmarketcap.com/v2/cryptocurrency/quotes/latest",
        params={"symbol": "BTC"},
        timeout=4.0,
    )


def test_request_raises_typed_error_on_timeout(client):
    instance, session = client
    error = Timeout("request timed out")
    session.get.side_effect = error

    with pytest.raises(CoinMarketCapError) as raised:
        instance.get_fear_and_greed()

    assert raised.value.__cause__ is error


def test_request_raises_typed_error_on_invalid_response(client):
    instance, session = client
    session.get.return_value = response({"data": {"value": "invalid"}})

    with pytest.raises(
        CoinMarketCapError,
        match="invalid response",
    ):
        instance.get_fear_and_greed()
