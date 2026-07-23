from requests import Session
from requests.exceptions import RequestException


class DiscordClientError(RuntimeError):
    pass


class DiscordClient:
    def __init__(
        self,
        webhook_key: str,
        session: Session | None = None,
        timeout: float = 10.0,
    ):
        self.webhook_key = webhook_key
        self.session = session or Session()
        self.timeout = timeout

    def post(self, payload: object) -> None:
        url = "https://discord.com/api/webhooks/" + self.webhook_key
        try:
            response = self.session.post(
                url,
                json=payload,
                timeout=self.timeout,
            )
        except RequestException as exception:
            raise DiscordClientError("Discord webhook request failed") from exception

        if response.status_code != 204:
            raise DiscordClientError(
                f"Discord webhook returned {response.status_code}: {response.text}"
            )
