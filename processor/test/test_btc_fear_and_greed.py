from types import SimpleNamespace
from unittest.mock import create_autospec, patch

import pytest
from requests import Session
from requests.exceptions import Timeout

from btc_fear_and_greed import BtcFngDiscordRunner, CoinMarketCapError
from discord.discord_client import DiscordClient


@pytest.fixture
def runner():
    session = Session()
    session.get = create_autospec(session.get)
    discord = create_autospec(DiscordClient, instance=True)
    instance = BtcFngDiscordRunner(
        discord_webhook_key="webhook-id/token",
        cmc_api_key="cmc-key",
        session=session,
        discord=discord,
        timeout=4.0,
    )
    yield instance, session, discord
    session.close()


def test_cmc_request_reuses_session_and_enforces_timeout(runner):
    instance, session, _ = runner
    session.get.return_value = SimpleNamespace(
        status_code=200,
        text='{"data": {"value": "42"}}',
    )

    result = instance.cmc_request("/v3/fear-and-greed/latest", {"limit": 1})

    assert result == {"data": {"value": "42"}}
    session.get.assert_called_once_with(
        "https://pro-api.coinmarketcap.com/v3/fear-and-greed/latest",
        params={"limit": 1},
        timeout=4.0,
    )


def test_cmc_request_raises_typed_error_on_timeout(runner):
    instance, session, _ = runner
    error = Timeout("request timed out")
    session.get.side_effect = error

    with pytest.raises(CoinMarketCapError) as raised:
        instance.cmc_request("/v3/fear-and-greed/latest", {})

    assert raised.value.__cause__ is error


def test_runner_delegates_webhook_delivery_to_discord_client(runner):
    instance, _, discord = runner
    instance.cmc_request = create_autospec(
        instance.cmc_request,
        side_effect=[
            {"data": {"value_classification": "Extreme fear", "value": "20"}},
            {"data": {"BTC": [{"quote": {"USD": {"price": 50_000}}}]}},
        ],
    )

    with patch("btc_fear_and_greed.log", autospec=True):
        instance.run()

    discord.post.assert_called_once()
    payload = discord.post.call_args.args[0]
    assert payload["embeds"][0]["title"] == ":scream: Extreme fear: 20"
