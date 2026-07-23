from types import SimpleNamespace
from unittest.mock import create_autospec

import pytest
from requests import Session
from requests.exceptions import Timeout

from discord.discord_client import DiscordClient, DiscordClientError


@pytest.fixture
def discord_client():
    session = Session()
    session.post = create_autospec(session.post)
    client = DiscordClient(
        webhook_key="webhook-id/token",
        parent="test",
        session=session,
        timeout=3.0,
    )
    yield client, session
    session.close()


def test_post_raises_typed_error_on_timeout(discord_client):
    client, session = discord_client
    error = Timeout("request timed out")
    session.post.side_effect = error
    payload = {"content": "test"}

    with pytest.raises(DiscordClientError) as raised:
        client.post(payload)

    session.post.assert_called_once_with(
        "https://discord.com/api/webhooks/webhook-id/token",
        json=payload,
        timeout=3.0,
    )
    assert raised.value.__cause__ is error


def test_post_raises_typed_error_on_non_success_response(discord_client):
    client, session = discord_client
    session.post.return_value = SimpleNamespace(status_code=500, text="server error")

    with pytest.raises(DiscordClientError, match="returned 500: server error"):
        client.post({"content": "test"})


def test_post_accepts_discord_no_content_response(discord_client):
    client, session = discord_client
    session.post.return_value = SimpleNamespace(status_code=204, text="")

    assert client.post({"content": "test"}) is None
