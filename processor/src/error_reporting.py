import logging
import traceback
from collections.abc import Callable, Mapping
from datetime import datetime, timezone
from uuid import uuid4

from discord.client import DiscordClient

TRACEBACK_LIMIT = 3400
FIELD_VALUE_LIMIT = 800
ERROR_MESSAGE_DESCRIPTION_LIMIT = 4000


def _new_incident_id() -> str:
    return uuid4().hex[:8]


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class ErrorReporter:
    def __init__(
        self,
        discord: DiscordClient | None = None,
        *,
        environment: str,
        incident_id_factory: Callable[[], str] = _new_incident_id,
        clock: Callable[[], datetime] = _utc_now,
    ) -> None:
        self.discord = discord
        self.environment = environment
        self.incident_id_factory = incident_id_factory
        self.clock = clock

    def report(
        self,
        exception: BaseException,
        *,
        logger: logging.Logger,
        source: str,
        operation: str,
        context: Mapping[str, object] | None = None,
    ) -> str:
        incident_id = self.incident_id_factory()
        self._log_exception(
            exception,
            logger=logger,
            incident_id=incident_id,
            source=source,
            operation=operation,
            context=context,
        )

        if self.discord is None:
            return incident_id

        try:
            self.discord.post_error(
                self._create_discord_payload(
                    exception,
                    incident_id=incident_id,
                    source=source,
                    operation=operation,
                    context=context,
                )
            )
        except Exception as notification_exception:
            self._log_exception(
                notification_exception,
                logger=logger,
                incident_id=f"{incident_id}-notification",
                source="ErrorReporter",
                operation="send_discord_notification",
                context={"original_incident_id": incident_id},
            )

        return incident_id

    def report_error_message(
        self,
        message: str,
        *,
        logger: logging.Logger,
        source: str,
        operation: str,
        context: Mapping[str, object] | None = None,
    ) -> str:
        incident_id = self.incident_id_factory()
        logger.error(
            "source=%s operation=%s context=%s error=%s",
            source,
            operation,
            self._format_context(context),
            message,
        )

        if self.discord is None:
            return incident_id

        try:
            self.discord.post_error(
                self._create_error_message_payload(
                    message,
                    incident_id=incident_id,
                    source=source,
                    operation=operation,
                    context=context,
                )
            )
        except Exception as notification_exception:
            self._log_exception(
                notification_exception,
                logger=logger,
                incident_id=f"{incident_id}-notification",
                source="ErrorReporter",
                operation="send_discord_error_message",
                context={"original_incident_id": incident_id},
            )

        return incident_id

    def report_warning_message(
        self,
        message: str,
        *,
        logger: logging.Logger,
        source: str,
        operation: str,
        context: Mapping[str, object] | None = None,
    ) -> str:
        warning_id = self.incident_id_factory()
        logger.warning(
            "source=%s operation=%s context=%s warning=%s",
            source,
            operation,
            self._format_context(context),
            message,
        )

        if self.discord is None:
            return warning_id

        try:
            self.discord.post_error(
                self._create_warning_message_payload(
                    message,
                    warning_id=warning_id,
                    source=source,
                    operation=operation,
                    context=context,
                )
            )
        except Exception as notification_exception:
            self._log_exception(
                notification_exception,
                logger=logger,
                incident_id=f"{warning_id}-notification",
                source="ErrorReporter",
                operation="send_discord_warning_message",
                context={"original_warning_id": warning_id},
            )

        return warning_id

    def _log_exception(
        self,
        exception: BaseException,
        *,
        logger: logging.Logger,
        incident_id: str,
        source: str,
        operation: str,
        context: Mapping[str, object] | None,
    ) -> None:
        logger.error(self._boundary("START", incident_id))
        logger.error(
            "source=%s operation=%s context=%s",
            source,
            operation,
            self._format_context(context),
            exc_info=(
                type(exception),
                exception,
                exception.__traceback__,
            ),
        )
        logger.error(self._boundary("END", incident_id))

    def _create_discord_payload(
        self,
        exception: BaseException,
        *,
        incident_id: str,
        source: str,
        operation: str,
        context: Mapping[str, object] | None,
    ) -> dict[str, object]:
        traceback_text = "".join(
            traceback.format_exception(
                type(exception),
                exception,
                exception.__traceback__,
            )
        ).replace("```", "'''")
        traceback_text = self._truncate_tail(
            traceback_text,
            TRACEBACK_LIMIT,
        )
        timestamp = self.clock()
        if timestamp.tzinfo is None:
            timestamp = timestamp.replace(tzinfo=timezone.utc)

        fields: list[dict[str, object]] = [
            {
                "name": "Incident",
                "value": incident_id,
                "inline": True,
            },
            {
                "name": "Environment",
                "value": self._truncate(self.environment, FIELD_VALUE_LIMIT),
                "inline": True,
            },
            {
                "name": "Operation",
                "value": self._truncate(operation, FIELD_VALUE_LIMIT),
                "inline": True,
            },
            {
                "name": "Exception",
                "value": self._truncate(
                    f"{type(exception).__name__}: {exception}",
                    FIELD_VALUE_LIMIT,
                ),
                "inline": False,
            },
        ]
        if context:
            fields.append({
                "name": "Context",
                "value": self._truncate(
                    self._format_context(context),
                    FIELD_VALUE_LIMIT,
                ),
                "inline": False,
            })

        return {
            "username": "Trading Processor Error Reporter",
            "embeds": [{
                "title": self._truncate(
                    f"Application error: {source}",
                    256,
                ),
                "description": f"```text\n{traceback_text}\n```",
                "color": 0xE74C3C,
                "fields": fields,
                "timestamp": timestamp.astimezone(timezone.utc).isoformat(),
            }],
        }

    def _create_error_message_payload(
        self,
        message: str,
        *,
        incident_id: str,
        source: str,
        operation: str,
        context: Mapping[str, object] | None,
    ) -> dict[str, object]:
        timestamp = self.clock()
        if timestamp.tzinfo is None:
            timestamp = timestamp.replace(tzinfo=timezone.utc)

        fields: list[dict[str, object]] = [
            {
                "name": "Incident",
                "value": incident_id,
                "inline": True,
            },
            {
                "name": "Environment",
                "value": self._truncate(self.environment, FIELD_VALUE_LIMIT),
                "inline": True,
            },
            {
                "name": "Operation",
                "value": self._truncate(operation, FIELD_VALUE_LIMIT),
                "inline": True,
            },
        ]
        if context:
            fields.append({
                "name": "Context",
                "value": self._truncate(
                    self._format_context(context),
                    FIELD_VALUE_LIMIT,
                ),
                "inline": False,
            })

        return {
            "username": "Trading Processor Error Reporter",
            "embeds": [{
                "title": self._truncate(
                    f"Application error: {source}",
                    256,
                ),
                "description": self._truncate(
                    message,
                    ERROR_MESSAGE_DESCRIPTION_LIMIT,
                ),
                "color": 0xE74C3C,
                "fields": fields,
                "timestamp": timestamp.astimezone(timezone.utc).isoformat(),
            }],
        }

    def _create_warning_message_payload(
        self,
        message: str,
        *,
        warning_id: str,
        source: str,
        operation: str,
        context: Mapping[str, object] | None,
    ) -> dict[str, object]:
        timestamp = self.clock()
        if timestamp.tzinfo is None:
            timestamp = timestamp.replace(tzinfo=timezone.utc)

        fields: list[dict[str, object]] = [
            {
                "name": "Warning",
                "value": warning_id,
                "inline": True,
            },
            {
                "name": "Environment",
                "value": self._truncate(self.environment, FIELD_VALUE_LIMIT),
                "inline": True,
            },
            {
                "name": "Operation",
                "value": self._truncate(operation, FIELD_VALUE_LIMIT),
                "inline": True,
            },
        ]
        if context:
            fields.append({
                "name": "Context",
                "value": self._truncate(
                    self._format_context(context),
                    FIELD_VALUE_LIMIT,
                ),
                "inline": False,
            })

        return {
            "username": "Trading Processor Warning Reporter",
            "embeds": [{
                "title": self._truncate(
                    f"Application warning: {source}",
                    256,
                ),
                "description": self._truncate(
                    message,
                    ERROR_MESSAGE_DESCRIPTION_LIMIT,
                ),
                "color": 0xF1C40F,
                "fields": fields,
                "timestamp": timestamp.astimezone(timezone.utc).isoformat(),
            }],
        }

    @staticmethod
    def _boundary(position: str, incident_id: str) -> str:
        return (
            f"{'=' * 16} EXCEPTION {position} "
            f"[{incident_id}] {'=' * 16}"
        )

    @staticmethod
    def _format_context(
        context: Mapping[str, object] | None,
    ) -> str:
        if not context:
            return "none"
        return ", ".join(
            f"{key}={value!r}"
            for key, value in sorted(context.items())
        )

    @staticmethod
    def _truncate(value: str, limit: int) -> str:
        if len(value) <= limit:
            return value
        return value[:limit - 3] + "..."

    @staticmethod
    def _truncate_tail(value: str, limit: int) -> str:
        if len(value) <= limit:
            return value
        marker = "... traceback truncated ...\n"
        return marker + value[-(limit - len(marker)):]
