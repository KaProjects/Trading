import logging
from dataclasses import dataclass
from decimal import Decimal

from cmc.client import CoinMarketCapClient
from cmc.models import FearAndGreedClassification, FearAndGreedReading
from discord.discord_client import DiscordClient

logger = logging.getLogger(__name__)


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
    def __init__(
        self,
        discord_webhook_key: str,
        cmc_api_key: str,
        client: CoinMarketCapClient | None = None,
        discord: DiscordClient | None = None,
        timeout: float = 10.0,
    ) -> None:
        self.client = client or CoinMarketCapClient(
            api_key=cmc_api_key,
            timeout=timeout,
        )
        self.discord = discord or DiscordClient(
            webhook_key=discord_webhook_key,
            timeout=timeout,
        )

    def run(self) -> None:
        try:
            reading = self.client.get_fear_and_greed()
            quote = self.client.get_btc_price()
            style = CLASSIFICATION_STYLES[reading.classification]

            logger.info(
                "%s %s: %s $%.0f",
                style.emoji,
                reading.classification,
                reading.value,
                quote.price,
            )
            if reading.value < 30 or reading.value >= 70:
                self.discord.post(
                    self._create_discord_payload(reading, quote.price)
                )
        except Exception:
            logger.exception("BTC fear-and-greed job failed")

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
