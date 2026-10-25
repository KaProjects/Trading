from datetime import date, datetime, timezone
from types import SimpleNamespace
from unittest.mock import Mock, call, create_autospec, patch

import pytest
from requests import Session
from requests.exceptions import Timeout

from polygon.client import (
    RATE_LIMIT_MAX_RETRIES,
    RATE_LIMIT_RETRY_DELAY_SECONDS,
    PolygonClient,
    PolygonClientError,
)


@pytest.fixture
def client():
    session = Session()
    session.get = create_autospec(session.get)
    sleeper = Mock()
    instance = PolygonClient(
        api_key="polygon-key",
        session=session,
        timeout=4.0,
        sleeper=sleeper,
    )
    yield instance, session, sleeper
    session.close()


def response(payload, status_code=200):
    return SimpleNamespace(
        status_code=status_code,
        text="response body",
        json=Mock(return_value=payload),
    )


def news_payload():
    return {
        "count": 1,
        "request_id": "request-123",
        "status": "OK",
        "results": [{
            "id": "article-123",
            "publisher": {
                "name": "Example Finance",
                "homepage_url": "https://example.com",
            },
            "title": "Example company announces quarterly results",
            "author": "Example Author",
            "published_utc": "2026-08-26T14:30:00Z",
            "article_url": "https://example.com/article-123",
            "tickers": ["AAPL"],
            "description": "Example article description.",
            "keywords": ["earnings"],
            "insights": [{
                "ticker": "AAPL",
                "sentiment": "positive",
                "sentiment_reasoning": "Results exceeded expectations.",
            }],
        }],
    }


@patch("polygon.client.date")
def test_get_latest_news_uses_eight_day_utc_window(
    current_date,
    client,
):
    instance, session, _ = client
    current_date.today.return_value = date(2026, 8, 26)
    session.get.return_value = response(news_payload())

    result = instance.get_latest_news()

    assert result.count == 1
    assert result.results[0].title == (
        "Example company announces quarterly results"
    )
    assert result.results[0].published_utc == datetime(
        2026,
        8,
        26,
        14,
        30,
        tzinfo=timezone.utc,
    )
    assert result.results[0].insights[0].sentiment == "positive"
    assert session.headers["Authorization"] == "Bearer polygon-key"
    session.get.assert_called_once_with(
        "https://api.polygon.io/v2/reference/news",
        params={
            "published_utc.gte": "2026-08-19T00:00:00Z",
            "published_utc.lt": "2026-08-27T00:00:00Z",
            "sort": "published_utc",
            "order": "desc",
            "limit": 1000,
        },
        timeout=4.0,
    )


def test_get_latest_news_raises_typed_error_on_timeout(client):
    instance, session, _ = client
    error = Timeout("request timed out")
    session.get.side_effect = error

    with pytest.raises(PolygonClientError, match="request failed") as raised:
        instance.get_latest_news()

    assert raised.value.__cause__ is error


def test_get_latest_news_can_disable_date_window(client):
    instance, session, _ = client
    session.get.return_value = response(news_payload())

    instance.get_latest_news(previous_days=None)

    session.get.assert_called_once_with(
        "https://api.polygon.io/v2/reference/news",
        params={
            "sort": "published_utc",
            "order": "desc",
            "limit": 1000,
        },
        timeout=4.0,
    )


def test_get_latest_news_rejects_negative_window(client):
    instance, session, _ = client

    with pytest.raises(ValueError, match="must not be negative"):
        instance.get_latest_news(previous_days=-1)

    session.get.assert_not_called()


def test_get_latest_news_raises_typed_error_on_invalid_response(client):
    instance, session, _ = client
    session.get.return_value = response({"results": [{"id": "incomplete"}]})

    with pytest.raises(PolygonClientError, match="invalid response"):
        instance.get_latest_news()


def test_get_latest_news_filters_by_ticker_and_custom_limit(client):
    instance, session, _ = client
    session.get.return_value = response(news_payload())

    instance.get_latest_news(ticker="BRK.B", limit=7)

    parameters = session.get.call_args.kwargs["params"]
    assert parameters["ticker"] == "BRK.B"
    assert parameters["limit"] == 7


def test_get_latest_news_retries_rate_limit_three_times(client, caplog):
    instance, session, sleeper = client
    session.get.side_effect = [
        response({}, status_code=429),
        response({}, status_code=429),
        response({}, status_code=429),
        response(news_payload()),
    ]

    result = instance.get_latest_news(ticker="AAPL", limit=5)

    assert result.count == 1
    assert session.get.call_count == 4
    assert sleeper.call_args_list == [
        call(RATE_LIMIT_RETRY_DELAY_SECONDS),
        call(RATE_LIMIT_RETRY_DELAY_SECONDS),
        call(RATE_LIMIT_RETRY_DELAY_SECONDS),
    ]
    assert caplog.text.count("Polygon rate limited; retrying") == 3


def test_get_latest_news_raises_after_rate_limit_retries(client, caplog):
    instance, session, sleeper = client
    session.get.return_value = response({}, status_code=429)

    with pytest.raises(PolygonClientError, match="Polygon returned 429"):
        instance.get_latest_news()

    assert session.get.call_count == RATE_LIMIT_MAX_RETRIES + 1
    assert sleeper.call_count == RATE_LIMIT_MAX_RETRIES
    assert "still rate limited after 3 retries" in caplog.text
