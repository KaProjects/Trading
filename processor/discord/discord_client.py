from utils import BaseClass
from requests import Session
from requests.exceptions import ConnectionError, Timeout, TooManyRedirects
import json


class DiscordClient(BaseClass):
    def __init__(self, webhook_key, **kwargs):
        super().__init__(**kwargs)
        self.webhook_key = webhook_key

    def post(self, payload: object):
        url = "https://discord.com/api/webhooks/" + self.webhook_key
        headers = {"Content-Type": "application/json"}

        session = Session()
        session.headers.update(headers)

        try:
            response = session.post(url, data=json.dumps(payload))
            if response.status_code != 204:
                self.log("response " + str(response.status_code) + " " + response.text)
        except (ConnectionError, Timeout, TooManyRedirects) as e:
            self.log("Error: " + str(e))
