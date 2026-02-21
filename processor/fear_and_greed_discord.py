import json
import traceback

from requests import Session
from requests.exceptions import ConnectionError, Timeout, TooManyRedirects

import utils


def log(message: str):
    utils.log("FNG", message)


class BtcFngDiscordRunner:
    def __init__(self, discord_webhook_key, cmc_api_key):
        self.discord_webhook_key = discord_webhook_key
        self.cmc_api_key = cmc_api_key
        self.last_value = -1

    def run(self):
        try:
            data = self.cmc_request("/v3/fear-and-greed/latest", {})
            if data and "data" in data:
                classification = data["data"]["value_classification"]
                new_value = int(data["data"]["value"])

                btc_data = self.cmc_request("/v2/cryptocurrency/quotes/latest", {'symbol': 'BTC'})
                if btc_data and "data" in btc_data:
                    btc_price = btc_data["data"]["BTC"][0]["quote"]["USD"]["price"]
                    message = "BTC=${:.0f} & {}: {}".format(btc_price, classification, new_value)
                    log(message)
                    if new_value not in range(30, 70):
                        self.discord_post({"content": message})
                else:
                    log("Error: Invalid BTC data: {}".format(btc_data))

                self.last_value = new_value
            else:
                log(data)
        except Exception:
            log(traceback.format_exc())
            log("^^^ exception occurred!")

    def cmc_request(self, path: str, parameters: object):
        url = "https://pro-api.coinmarketcap.com" + path
        headers = {
            "Accepts": "application/json",
            "X-CMC_PRO_API_KEY": self.cmc_api_key,
        }

        session = Session()
        session.headers.update(headers)

        try:
            response = session.get(url, params=parameters)
            if response.status_code != 200:
                log(response.text)
            else:
                return json.loads(response.text)
        except (ConnectionError, Timeout, TooManyRedirects) as e:
            log(e)

    def discord_post(self, payload: object):
        url = "https://discord.com/api/webhooks/" + self.discord_webhook_key
        headers = {"Content-Type": "application/json"}

        session = Session()
        session.headers.update(headers)

        try:
            response = session.post(url, data=json.dumps(payload))
            if response.status_code != 204:
                log(response.text)
        except (ConnectionError, Timeout, TooManyRedirects) as e:
            log(e)
