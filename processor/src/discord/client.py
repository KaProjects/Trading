import logging
import time
from collections.abc import Callable

from requests import Response, Session
from requests.exceptions import RequestException

DISCORD_API_URL = "https://discord.com/api/v10"
DISCORD_CHANNEL_URL = "https://discord.com/channels"
DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks"
TEXT_CHANNEL_TYPES = {0, 5}
RATE_LIMIT_MAX_RETRIES = 3
RATE_LIMIT_MAX_WAIT_SECONDS = 30.0
RATE_LIMIT_SAFETY_MARGIN_SECONDS = 0.05
SUPPRESS_NOTIFICATIONS_FLAG = 1 << 12

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
    ) -> str | None:
        normalized_name = self._normalize_channel_name(channel_name)
        channel_id = self._resolve_channel_id(normalized_name)
        if channel_id is None:
            return None

        response = self._post_to_channel(channel_id, payload)
        message_id = self._message_id(response)
        return (
            f"{DISCORD_CHANNEL_URL}/{self.guild_id}/{channel_id}/{message_id}"
        )

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
            response = self._request_with_rate_limit_retry(
                lambda: self.session.get(
                    url,
                    headers=self._headers(),
                    timeout=self.timeout,
                ),
                operation="channel lookup",
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
    ) -> Response:
        url = f"{DISCORD_API_URL}/channels/{channel_id}/messages"
        message_payload = dict(payload)
        flags = message_payload.get("flags", 0)
        if not isinstance(flags, int):
            raise DiscordClientError("Discord message flags must be an integer")
        message_payload["flags"] = flags | SUPPRESS_NOTIFICATIONS_FLAG
        try:
            response = self._request_with_rate_limit_retry(
                lambda: self.session.post(
                    url,
                    headers=self._headers(),
                    json=message_payload,
                    timeout=self.timeout,
                ),
                operation="message",
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
        return response

    def _post_to_webhook(
        self,
        webhook_key: str,
        payload: dict[str, object],
    ) -> None:
        url = f"{DISCORD_WEBHOOK_URL}/{webhook_key}"
        try:
            response = self._request_with_rate_limit_retry(
                lambda: self.session.post(
                    url,
                    json=payload,
                    timeout=self.timeout,
                ),
                operation="webhook",
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

    @staticmethod
    def _request_with_rate_limit_retry(
        request: Callable[[], Response],
        *,
        operation: str,
    ) -> Response:
        retries = 0
        waited = 0.0

        while True:
            response = request()
            if response.status_code != 429:
                return response

            retry_after = DiscordClient._retry_after(response)
            if retry_after is None:
                logger.warning(
                    "Discord %s rate limit response has no retry delay",
                    operation,
                )
                return response

            wait = retry_after + RATE_LIMIT_SAFETY_MARGIN_SECONDS
            if retries >= RATE_LIMIT_MAX_RETRIES:
                logger.warning(
                    "Discord %s is still rate limited after %d retries",
                    operation,
                    RATE_LIMIT_MAX_RETRIES,
                )
                return response

            if waited + wait > RATE_LIMIT_MAX_WAIT_SECONDS:
                logger.warning(
                    "Discord %s rate limit exceeds the %.0f-second "
                    "retry budget",
                    operation,
                    RATE_LIMIT_MAX_WAIT_SECONDS,
                )
                return response

            retries += 1
            waited += wait
            logger.warning(
                "Discord %s rate limited; retrying in %.3f seconds "
                "(%d/%d)",
                operation,
                wait,
                retries,
                RATE_LIMIT_MAX_RETRIES,
            )
            time.sleep(wait)

    @staticmethod
    def _retry_after(response: Response) -> float | None:
        try:
            data = response.json()
        except (RequestException, ValueError):
            data = None

        candidates = []
        if isinstance(data, dict):
            candidates.append(data.get("retry_after"))

        headers = getattr(response, "headers", {}) or {}
        candidates.extend([
            headers.get("Retry-After"),
            headers.get("X-RateLimit-Reset-After"),
        ])
        for candidate in candidates:
            try:
                retry_after = float(candidate)
            except (TypeError, ValueError):
                continue
            if retry_after >= 0:
                return retry_after
        return None

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bot {self.bot_token}",
            "Content-Type": "application/json",
        }

    @staticmethod
    def _message_id(response: Response) -> str:
        try:
            data = response.json()
        except (RequestException, ValueError) as exception:
            raise DiscordClientError(
                "Discord message response was not valid JSON"
            ) from exception
        if not isinstance(data, dict) or not data.get("id"):
            raise DiscordClientError(
                "Discord message response did not contain a message ID"
            )
        return str(data["id"])

    @staticmethod
    def _normalize_channel_name(channel_name: str) -> str:
        normalized_name = channel_name.strip().lstrip("#").casefold()
        if not normalized_name:
            raise ValueError("Discord channel name cannot be empty")
        return normalized_name
