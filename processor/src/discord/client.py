import logging

from requests import Session
from requests.exceptions import RequestException

DISCORD_API_URL = "https://discord.com/api/v10"
DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks"
TEXT_CHANNEL_TYPES = {0, 5}

logger = logging.getLogger(__name__)


class DiscordClientError(RuntimeError):
    pass


class DiscordClient:
    def __init__(
        self,
        bot_token: str,
        guild_id: str,
        btc_webhook_key: str,
        eventlog_webhook_key: str,
        earnings_webhook_key: str,
        errorlog_webhook_key: str,
        session: Session | None = None,
        timeout: float = 10.0,
    ) -> None:
        self.bot_token = bot_token
        self.guild_id = guild_id
        self.btc_webhook_key = btc_webhook_key
        self.eventlog_webhook_key = eventlog_webhook_key
        self.earnings_webhook_key = earnings_webhook_key
        self.errorlog_webhook_key = errorlog_webhook_key
        self.session = session or Session()
        self.timeout = timeout
        self._channel_ids: dict[str, str] | None = None

    def post(
        self,
        channel_name: str,
        payload: dict[str, object],
    ) -> None:
        normalized_name = self._normalize_channel_name(channel_name)
        try:
            channel_id = self._resolve_channel_id(normalized_name)
        except DiscordClientError as exception:
            self._post_fallback(channel_name, payload, str(exception))
            return

        if channel_id is None:
            self._post_fallback(
                channel_name,
                payload,
                f"channel #{channel_name} was not found",
            )
            return

        self._post_to_channel(channel_id, payload)

    def channel_exists(self, channel_name: str) -> bool:
        normalized_name = self._normalize_channel_name(channel_name)
        return self._resolve_channel_id(normalized_name) is not None

    def post_if_channel_exists(
        self,
        channel_name: str,
        payload: dict[str, object],
    ) -> bool:
        normalized_name = self._normalize_channel_name(channel_name)
        channel_id = self._resolve_channel_id(normalized_name)
        if channel_id is None:
            return False

        self._post_to_channel(channel_id, payload)
        return True

    def post_btc(self, payload: dict[str, object]) -> None:
        self._post_to_webhook(self.btc_webhook_key, payload)

    def post_eventlog(self, payload: dict[str, object]) -> None:
        self._post_to_webhook(self.eventlog_webhook_key, payload)

    def post_earnings(self, payload: dict[str, object]) -> None:
        self._post_to_webhook(self.earnings_webhook_key, payload)

    def post_error(self, payload: dict[str, object]) -> None:
        self._post_to_webhook(self.errorlog_webhook_key, payload)

    def _resolve_channel_id(self, normalized_name: str) -> str | None:
        cache_was_loaded = self._channel_ids is not None
        if self._channel_ids is None:
            self._refresh_channels()

        channel_id = self._channel_ids.get(normalized_name)
        if channel_id is None and cache_was_loaded:
            self._refresh_channels()
            channel_id = self._channel_ids.get(normalized_name)
        return channel_id

    def _refresh_channels(self) -> None:
        url = f"{DISCORD_API_URL}/guilds/{self.guild_id}/channels"
        try:
            response = self.session.get(
                url,
                headers=self._headers(),
                timeout=self.timeout,
            )
        except RequestException as exception:
            raise DiscordClientError(
                "Discord channel lookup request failed"
            ) from exception

        if not 200 <= response.status_code < 300:
            raise DiscordClientError(
                "Discord channel lookup returned "
                f"{response.status_code}: {response.text}"
            )

        self._channel_ids = {
            channel["name"].casefold(): str(channel["id"])
            for channel in response.json()
            if channel.get("type") in TEXT_CHANNEL_TYPES
        }

    def _post_fallback(
        self,
        channel_name: str,
        payload: dict[str, object],
        reason: str,
    ) -> None:
        logger.warning(
            "Redirecting Discord message for #%s to errorlog: %s",
            channel_name,
            reason,
        )
        fallback_payload = dict(payload)
        notice = f"Delivery fallback for #{channel_name}: {reason}"
        content = fallback_payload.get("content")
        if isinstance(content, str) and content:
            notice = f"{notice}\n{content}"
        fallback_payload["content"] = notice[:2000]
        self.post_error(fallback_payload)

    def _post_to_channel(
        self,
        channel_id: str,
        payload: dict[str, object],
    ) -> None:
        url = f"{DISCORD_API_URL}/channels/{channel_id}/messages"
        try:
            response = self.session.post(
                url,
                headers=self._headers(),
                json=payload,
                timeout=self.timeout,
            )
        except RequestException as exception:
            raise DiscordClientError(
                "Discord message request failed"
            ) from exception

        if not 200 <= response.status_code < 300:
            raise DiscordClientError(
                f"Discord message returned {response.status_code}: "
                f"{response.text}"
            )

    def _post_to_webhook(
        self,
        webhook_key: str,
        payload: dict[str, object],
    ) -> None:
        url = f"{DISCORD_WEBHOOK_URL}/{webhook_key}"
        try:
            response = self.session.post(
                url,
                json=payload,
                timeout=self.timeout,
            )
        except RequestException as exception:
            raise DiscordClientError(
                "Discord webhook request failed"
            ) from exception

        if not 200 <= response.status_code < 300:
            raise DiscordClientError(
                f"Discord webhook returned {response.status_code}: "
                f"{response.text}"
            )

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bot {self.bot_token}",
            "Content-Type": "application/json",
        }

    @staticmethod
    def _normalize_channel_name(channel_name: str) -> str:
        normalized_name = channel_name.strip().lstrip("#").casefold()
        if not normalized_name:
            raise ValueError("Discord channel name cannot be empty")
        return normalized_name
