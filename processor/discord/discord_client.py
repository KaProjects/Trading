import json

from requests import Session
from requests.exceptions import ConnectionError, Timeout, TooManyRedirects

from utils import BaseClass


class DiscordClient(BaseClass):
    def __init__(self, webhook_key, parent, **kwargs):
        super().__init__(identity=parent+".DiscordClient", **kwargs)
        self.webhook_key = webhook_key

    def post(self, payload: object):
        url = "https://discord.com/api/webhooks/" + self.webhook_key
        headers = {"Content-Type": "application/json"}

        session = Session()
        session.headers.update(headers)

        try:
            response = session.post(url, data=json.dumps(payload))
            if response.status_code != 204:
                self.log.error("response " + str(response.status_code) + " " + response.text)
        except (ConnectionError, Timeout, TooManyRedirects) as e:
            self.log.exception(e)
