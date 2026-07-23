from decimal import Decimal
from unittest.mock import create_autospec, patch

import pytest

from cmc.client import CoinMarketCapClient
from cmc.models import (
    BitcoinQuote,
    FearAndGreedClassification,
    FearAndGreedReading,
)
from cmc.retriever import BtcFearAndGreedRetrieverRunner
from discord.client import DiscordClient


@pytest.fixture
def runner():
    client = create_autospec(CoinMarketCapClient, instance=True)
    discord = create_autospec(DiscordClient, instance=True)
    instance = BtcFearAndGreedRetrieverRunner(
        discord_webhook_key="webhook-id/token",
        cmc_api_key="cmc-key",
        client=client,
        discord=discord,
    )
    return instance, client, discord


def test_runner_delegates_webhook_delivery_to_discord_client(runner):
    instance, client, discord = runner
    client.get_fear_and_greed.return_value = FearAndGreedReading.model_validate({
        "data": {
            "value": "20",
            "value_classification": "Extreme fear",
        },
    })
    client.get_btc_price.return_value = BitcoinQuote.model_validate({
        "data": {
            "BTC": [{
                "quote": {
                    "USD": {"price": 50_000},
                },
            }],
        },
    })

    instance.run()

    discord.post.assert_called_once()
    payload = discord.post.call_args.args[0]
    embed = payload["embeds"][0]
    assert embed["title"] == ":scream: Extreme fear: 20"
    assert embed["description"] == (
        ":coin: $50000 ... buy the dip? :bulb:"
    )


@pytest.mark.parametrize("value", [30, 69])
def test_runner_does_not_post_neutral_range(value, runner):
    instance, client, discord = runner
    client.get_fear_and_greed.return_value = FearAndGreedReading.model_validate({
        "data": {
            "value": value,
            "value_classification": (
                FearAndGreedClassification.NEUTRAL.value
            ),
        },
    })
    client.get_btc_price.return_value = BitcoinQuote.model_validate({
        "data": {
            "BTC": [{
                "quote": {
                    "USD": {"price": Decimal("50000")},
                },
            }],
        },
    })

    instance.run()

    discord.post.assert_not_called()


def test_runner_logs_client_failures_without_posting(runner):
    instance, client, discord = runner
    client.get_fear_and_greed.side_effect = RuntimeError("unavailable")

    with patch("cmc.retriever.logger", autospec=True) as logger:
        instance.run()

    logger.exception.assert_called_once_with(
        "BTC fear-and-greed job failed"
    )
    discord.post.assert_not_called()
