from types import SimpleNamespace
from unittest.mock import call, create_autospec, patch

import pytest
from requests import Session
from requests.exceptions import Timeout

from discord.client import DiscordClient, DiscordClientError

API_URL = "https://discord.com/api/v10"
WEBHOOK_URL = "https://discord.com/api/webhooks"
HEADERS = {
    "Authorization": "Bot bot-token",
    "Content-Type": "application/json",
}


def response(status_code=200, *, data=None, text="", headers=None):
    return SimpleNamespace(
        status_code=status_code,
        text=text,
        headers=headers or {},
        json=lambda: data,
    )


@pytest.fixture
def discord_client():
    session = Session()
    session.get = create_autospec(session.get)
    session.post = create_autospec(session.post)
    session.get.return_value = response(
        data=[
            {"id": "btc-id", "name": "btc", "type": 0},
            {"id": "voice-id", "name": "voice", "type": 2},
        ]
    )
    session.post.return_value = response()
    client = DiscordClient(
        bot_token="bot-token",
        guild_id="guild-id",
        btc_webhook_key="btc-webhook",
        eventlog_webhook_key="eventlog-webhook",
        earnings_webhook_key="earnings-webhook",
        errorlog_webhook_key="errorlog-webhook",
        session=session,
        timeout=3.0,
    )
    yield client, session
    session.close()


def test_post_resolves_channel_name_and_caches_channel_id(discord_client):
    client, session = discord_client
    payload = {"content": "test"}

    client.post("btc", payload)
    client.post("#btc", payload)

    session.get.assert_called_once_with(
        f"{API_URL}/guilds/guild-id/channels",
        headers=HEADERS,
        timeout=3.0,
    )
    assert session.post.call_args_list == [
        call(
            f"{API_URL}/channels/btc-id/messages",
            headers=HEADERS,
            json=payload,
            timeout=3.0,
        ),
        call(
            f"{API_URL}/channels/btc-id/messages",
            headers=HEADERS,
            json=payload,
            timeout=3.0,
        ),
    ]


def test_channel_exists_checks_name_without_posting(discord_client):
    client, session = discord_client

    assert client.channel_exists("#BTC")
    assert not client.channel_exists("missing")

    assert session.get.call_count == 2
    session.post.assert_not_called()


def test_post_if_channel_exists_skips_missing_channel_without_fallback(
    discord_client,
):
    client, session = discord_client

    posted = client.post_if_channel_exists(
        "missing",
        {"content": "test"},
    )

    assert not posted
    session.post.assert_not_called()


def test_post_if_channel_exists_posts_to_resolved_channel(discord_client):
    client, session = discord_client
    payload = {"content": "test"}

    posted = client.post_if_channel_exists("btc", payload)

    assert posted
    session.post.assert_called_once_with(
        f"{API_URL}/channels/btc-id/messages",
        headers=HEADERS,
        json=payload,
        timeout=3.0,
    )


@pytest.mark.parametrize(
    ("method_name", "webhook_key"),
    [
        ("post_btc", "btc-webhook"),
        ("post_eventlog", "eventlog-webhook"),
        ("post_earnings", "earnings-webhook"),
    ],
)
def test_fixed_channel_methods_use_dedicated_webhooks(
    discord_client,
    method_name,
    webhook_key,
):
    client, session = discord_client
    payload = {"content": "test"}

    getattr(client, method_name)(payload)

    session.get.assert_not_called()
    session.post.assert_called_once_with(
        f"{WEBHOOK_URL}/{webhook_key}",
        json=payload,
        timeout=3.0,
    )


def test_missing_channel_redirects_message_to_errorlog(discord_client):
    client, session = discord_client
    payload = {"embeds": [{"title": "Target update"}]}

    client.post("AAPL", payload)

    session.post.assert_called_once_with(
        f"{WEBHOOK_URL}/errorlog-webhook",
        json={
            "content": (
                "Delivery fallback for #AAPL: "
                "channel #AAPL was not found"
            ),
            "embeds": [{"title": "Target update"}],
        },
        timeout=3.0,
    )


def test_post_error_uses_dedicated_webhook_without_channel_lookup(
    discord_client,
):
    client, session = discord_client
    payload = {"content": "application failed"}

    client.post_error(payload)

    session.get.assert_not_called()
    session.post.assert_called_once_with(
        f"{WEBHOOK_URL}/errorlog-webhook",
        json=payload,
        timeout=3.0,
    )


def test_post_raises_typed_error_on_timeout(discord_client):
    client, session = discord_client
    error = Timeout("request timed out")
    session.post.side_effect = error

    with pytest.raises(DiscordClientError) as raised:
        client.post("btc", {"content": "test"})

    assert raised.value.__cause__ is error


def test_post_raises_typed_error_on_non_success_response(discord_client):
    client, session = discord_client
    session.post.return_value = response(
        status_code=500,
        text="server error",
    )

    with pytest.raises(DiscordClientError, match="returned 500: server error"):
        client.post("btc", {"content": "test"})


def test_post_retries_rate_limit_using_discord_delay(
    discord_client,
    caplog,
):
    client, session = discord_client
    session.post.side_effect = [
        response(
            status_code=429,
            data={"retry_after": 0.322, "global": False},
            text="rate limited",
        ),
        response(),
    ]

    with patch("discord.client.time.sleep") as sleep:
        with caplog.at_level("WARNING", logger="discord.client"):
            client.post("btc", {"content": "test"})

    assert session.post.call_count == 2
    sleep.assert_called_once_with(pytest.approx(0.372))
    assert "Discord message rate limited" in caplog.text


def test_webhook_raises_after_three_rate_limit_retries(
    discord_client,
    caplog,
):
    client, session = discord_client
    session.post.return_value = response(
        status_code=429,
        data={"retry_after": 0.1},
        text="rate limited",
    )

    with patch("discord.client.time.sleep") as sleep:
        with caplog.at_level("WARNING", logger="discord.client"):
            with pytest.raises(
                DiscordClientError,
                match="Discord webhook returned 429: rate limited",
            ):
                client.post_eventlog({"content": "test"})

    assert session.post.call_count == 4
    assert sleep.call_count == 3
    assert "still rate limited after 3 retries" in caplog.text


def test_rate_limit_does_not_exceed_wait_budget(discord_client, caplog):
    client, session = discord_client
    session.post.return_value = response(
        status_code=429,
        data={"retry_after": 30},
        text="rate limited",
    )

    with patch("discord.client.time.sleep") as sleep:
        with caplog.at_level("WARNING", logger="discord.client"):
            with pytest.raises(DiscordClientError):
                client.post_eventlog({"content": "test"})

    session.post.assert_called_once()
    sleep.assert_not_called()
    assert "exceeds the 30-second retry budget" in caplog.text


def test_channel_lookup_failure_uses_errorlog_fallback(discord_client):
    client, session = discord_client
    session.get.side_effect = Timeout("lookup timed out")

    client.post("btc", {"content": "test"})

    fallback_payload = session.post.call_args.kwargs["json"]
    assert fallback_payload["content"].startswith(
        "Delivery fallback for #btc: Discord channel lookup request failed"
    )
    assert fallback_payload["content"].endswith("\ntest")
