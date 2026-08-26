import logging
from dataclasses import dataclass
from decimal import Decimal

from cmc.client import CoinMarketCapClient
from cmc.models import FearAndGreedClassification, FearAndGreedReading
from discord.client import DiscordClient
from error_reporting import ErrorReporter

RUNNER_NAME = "BtcFearAndGreed"
logger = logging.getLogger(RUNNER_NAME)


@dataclass(frozen=True, slots=True)
class ClassificationStyle:
    color: int
    emoji: str
    advice: str = ""


CLASSIFICATION_STYLES = {
    FearAndGreedClassification.EXTREME_FEAR: ClassificationStyle(
        color=0xFF0000,
        emoji=":scream:",
        advice="... buy the dip? :bulb:",
    ),
    FearAndGreedClassification.FEAR: ClassificationStyle(
        color=0xFF8C00,
        emoji=":fearful:",
    ),
    FearAndGreedClassification.NEUTRAL: ClassificationStyle(
        color=0xFFFF00,
        emoji=":scales:",
    ),
    FearAndGreedClassification.GREED: ClassificationStyle(
        color=0x00FF00,
        emoji=":money_mouth:",
    ),
    FearAndGreedClassification.EXTREME_GREED: ClassificationStyle(
        color=0x008000,
        emoji=":rocket:",
        advice="... take profits? :bulb:",
    ),
}


class BtcFearAndGreedRetrieverRunner:
    log = logger
    name = RUNNER_NAME

    def __init__(
        self,
        cmc_api_key: str | None = None,
        client: CoinMarketCapClient | None = None,
        discord: DiscordClient | None = None,
        error_reporter: ErrorReporter | None = None,
        timeout: float = 10.0,
    ) -> None:
        if client is None:
            if cmc_api_key is None:
                raise ValueError("cmc_api_key is required without a client")
            client = CoinMarketCapClient(
                api_key=cmc_api_key,
                timeout=timeout,
            )
        if discord is None:
            raise ValueError("discord is required")

        self.client = client
        self.discord = discord
        self.errors = error_reporter or ErrorReporter(environment="local")

    def run(self) -> None:
        try:
            reading = self.client.get_fear_and_greed()
            quote = self.client.get_btc_price()
            style = CLASSIFICATION_STYLES[reading.classification]

            self.log.info(
                "%s %s: %s $%.0f",
                style.emoji,
                reading.classification,
                reading.value,
                quote.price,
            )
            if reading.value < 30 or reading.value >= 70:
                self.discord.post_btc(
                    self._create_discord_payload(reading, quote.price)
                )
        except Exception as exception:
            self.errors.report(
                exception,
                logger=self.log,
                source=self.name,
                operation="run",
            )

    @staticmethod
    def _create_discord_payload(
        reading: FearAndGreedReading,
        btc_price: Decimal,
    ) -> dict[str, object]:
        style = CLASSIFICATION_STYLES[reading.classification]
        return {
            "embeds": [{
                "color": style.color,
                "title": (
                    f"{style.emoji} {reading.classification}: {reading.value}"
                ),
                "description": f":coin: ${btc_price:.0f} {style.advice}",
            }],
        }
