import json
import logging
from types import SimpleNamespace
from unittest.mock import create_autospec, patch

import pytest
from requests import Session
from requests.exceptions import Timeout

from discord.discord_client import DiscordClient


@pytest.fixture
def discord_client():
    session = Session()
    session.post = create_autospec(session.post)
    with patch("discord.discord_client.Session", autospec=True, return_value=session):
        client = object.__new__(DiscordClient)
        client.webhook_key = "webhook-id/token"
        client.log = create_autospec(logging.Logger, instance=True)
        yield client, session
    session.close()


def test_post_logs_timeout(discord_client):
    client, session = discord_client
    error = Timeout("request timed out")
    session.post.side_effect = error
    payload = {"content": "test"}

    client.post(payload)

    session.post.assert_called_once_with(
        "https://discord.com/api/webhooks/webhook-id/token",
        data=json.dumps(payload),
    )
    client.log.exception.assert_called_once_with(error)


def test_post_logs_non_success_response(discord_client):
    client, session = discord_client
    session.post.return_value = SimpleNamespace(status_code=500, text="server error")

    client.post({"content": "test"})

    client.log.error.assert_called_once_with("response 500 server error")
