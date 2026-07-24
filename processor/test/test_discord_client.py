from types import SimpleNamespace
from unittest.mock import call, create_autospec

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


def response(status_code=200, *, data=None, text=""):
    return SimpleNamespace(
        status_code=status_code,
        text=text,
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


def test_channel_lookup_failure_uses_errorlog_fallback(discord_client):
    client, session = discord_client
    session.get.side_effect = Timeout("lookup timed out")

    client.post("btc", {"content": "test"})

    fallback_payload = session.post.call_args.kwargs["json"]
    assert fallback_payload["content"].startswith(
        "Delivery fallback for #btc: Discord channel lookup request failed"
    )
    assert fallback_payload["content"].endswith("\ntest")
