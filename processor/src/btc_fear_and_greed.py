import json
import logging

from requests import Session
from requests.exceptions import RequestException

from discord.discord_client import DiscordClient

logger = logging.getLogger(__name__)


class CoinMarketCapError(RuntimeError):
    pass


class BtcFngDiscordRunner:
    def __init__(
        self,
        discord_webhook_key: str,
        cmc_api_key: str,
        session: Session | None = None,
        discord: DiscordClient | None = None,
        timeout: float = 10.0,
    ):
        self.cmc_api_key = cmc_api_key
        self.session = session or Session()
        self.session.headers.update({
            "Accepts": "application/json",
            "X-CMC_PRO_API_KEY": self.cmc_api_key,
        })
        self.discord = discord or DiscordClient(
            webhook_key=discord_webhook_key,
            timeout=timeout,
        )
        self.timeout = timeout
        self.last_value = -1
        self.classification_map = {"Extreme fear": [16711680, ":scream:", "... buy the dip? :bulb:"],
                                   "Fear": [16747520, ":fearful:", ""],
                                   "Neutral": [16776960, ":scales:", ""],
                                   "Greed": [65280, ":money_mouth:", ""],
                                   "Extreme greed": [32768, ":rocket:", "... take profits? :bulb:"]}

    def run(self):
        try:
            data = self.cmc_request("/v3/fear-and-greed/latest", {})
            if data and "data" in data:
                classification = data["data"]["value_classification"]
                new_value = int(data["data"]["value"])

                btc_data = self.cmc_request("/v2/cryptocurrency/quotes/latest", {'symbol': 'BTC'})
                if btc_data and "data" in btc_data:
                    btc_price = btc_data["data"]["BTC"][0]["quote"]["USD"]["price"]
                    message = "{} {}: {} ${:.0f}".format(self.classification_map[classification][1], classification, new_value, btc_price)
                    logger.info(message)
                    if new_value not in range(30, 70):
                        self.discord.post({"embeds": [{
                            "color": self.classification_map[classification][0],
                            "title": "{} {}: {}".format(self.classification_map[classification][1], classification, new_value),
                            "description": ":coin: ${:.0f} {}".format(btc_price, self.classification_map[classification][2]),
                        }]})
                else:
                    logger.error("Invalid BTC data: %r", btc_data)

                self.last_value = new_value
            else:
                logger.error("Invalid fear-and-greed data: %r", data)
        except Exception:
            logger.exception("BTC fear-and-greed job failed")

    def cmc_request(self, path: str, parameters: object):
        url = "https://pro-api.coinmarketcap.com" + path
        try:
            response = self.session.get(
                url,
                params=parameters,
                timeout=self.timeout,
            )
        except RequestException as exception:
            raise CoinMarketCapError("CoinMarketCap request failed") from exception

        if response.status_code != 200:
            raise CoinMarketCapError(
                f"CoinMarketCap returned {response.status_code}: {response.text}"
            )
        try:
            return json.loads(response.text)
        except (TypeError, ValueError) as exception:
            raise CoinMarketCapError("CoinMarketCap returned invalid JSON") from exception
