import asyncio
import json
import traceback

from requests import Session
from requests.exceptions import ConnectionError, Timeout, TooManyRedirects

import utils


def log(message: str):
    utils.log("FNG", message)


def request(path: str, parameters: object, api_key):
    url = "https://pro-api.coinmarketcap.com" + path
    headers = {
        "Accepts": "application/json",
        "X-CMC_PRO_API_KEY": api_key,
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
        print(e)


def post(payload: object, api_key: str):
    url = "https://discord.com/api/webhooks/" + api_key
    headers = {"Content-Type": "application/json"}

    session = Session()
    session.headers.update(headers)

    try:
        response = session.post(url, data=json.dumps(payload))
        if response.status_code != 204:
            log(response.text)
    except (ConnectionError, Timeout, TooManyRedirects) as e:
        log(e)


async def run(envs):
    discord_api_key = envs["discord_api_key"]
    cmc_api_key = envs["cmc_api_key"]
    latest_value = -1
    while True:
        try:
            data = request("/v3/fear-and-greed/latest", {}, cmc_api_key)
            log(data)
            if data and "data" in data:
                classification = data["data"]["value_classification"]
                new_value = int(data["data"]["value"])
                if latest_value//10 != new_value//10:
                    btc_data = request("/v2/cryptocurrency/quotes/latest", {'symbol': 'BTC'}, cmc_api_key)
                    if btc_data and "data" in btc_data:
                        btc_price = btc_data["data"]["BTC"][0]["quote"]["USD"]["price"]
                        message = "BTC=${:.0f} & {}: {} -> {}".format(btc_price, classification, latest_value, new_value)
                        log(message)
                        post({"content": message}, discord_api_key)
                    else:
                        log(data)
                latest_value = new_value
            else:
                log(data)
        except Exception:
            log(traceback.format_exc())
            log("^^^ exception occurred!")

        await asyncio.sleep(60 * 60)
