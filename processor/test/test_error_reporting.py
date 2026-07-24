import logging
from datetime import datetime, timezone
from unittest.mock import create_autospec

from discord.client import DiscordClient
from error_reporting import ErrorReporter


def raise_test_error(message: str = "service unavailable") -> None:
    raise RuntimeError(message)


def test_report_marks_traceback_and_posts_formatted_discord_embed():
    discord = create_autospec(DiscordClient, instance=True)
    logger = create_autospec(logging.Logger, instance=True)
    reporter = ErrorReporter(
        discord,
        environment="production",
        incident_id_factory=lambda: "abc12345",
        clock=lambda: datetime(
            2026,
            7,
            23,
            12,
            30,
            tzinfo=timezone.utc,
        ),
    )

    captured_error = None
    try:
        raise_test_error()
    except RuntimeError as error:
        captured_error = error
        incident_id = reporter.report(
            error,
            logger=logger,
            source="FinnhubEarnings",
            operation="process_company",
            context={"company_id": "AAPL"},
        )

    assert incident_id == "abc12345"
    assert captured_error is not None
    assert "EXCEPTION START [abc12345]" in logger.error.call_args_list[0].args[0]
    traceback_call = logger.error.call_args_list[1]
    assert traceback_call.kwargs["exc_info"][1] is captured_error
    assert "EXCEPTION END [abc12345]" in logger.error.call_args_list[2].args[0]

    discord.post_error.assert_called_once()
    embed = discord.post_error.call_args.args[0]["embeds"][0]
    assert embed["title"] == "Application error: FinnhubEarnings"
    assert "RuntimeError: service unavailable" in embed["description"]
    assert embed["timestamp"] == "2026-07-23T12:30:00+00:00"
    assert {
        field["name"]: field["value"]
        for field in embed["fields"]
    } == {
        "Incident": "abc12345",
        "Environment": "production",
        "Operation": "process_company",
        "Exception": "RuntimeError: service unavailable",
        "Context": "company_id='AAPL'",
    }


def test_discord_notification_failure_is_bounded_and_does_not_escape():
    discord = create_autospec(DiscordClient, instance=True)
    discord.post_error.side_effect = RuntimeError("Discord unavailable")
    logger = create_autospec(logging.Logger, instance=True)
    reporter = ErrorReporter(
        discord,
        environment="production",
        incident_id_factory=lambda: "abc12345",
    )

    try:
        raise_test_error()
    except RuntimeError as error:
        reporter.report(
            error,
            logger=logger,
            source="StockDataRetriever",
            operation="run",
        )

    assert logger.error.call_count == 6
    assert (
        "EXCEPTION START [abc12345-notification]"
        in logger.error.call_args_list[3].args[0]
    )
    assert (
        "EXCEPTION END [abc12345-notification]"
        in logger.error.call_args_list[5].args[0]
    )


def test_long_traceback_is_truncated_to_discord_embed_limit():
    discord = create_autospec(DiscordClient, instance=True)
    reporter = ErrorReporter(
        discord,
        environment="production",
        incident_id_factory=lambda: "abc12345",
    )

    try:
        raise_test_error("x" * 5000)
    except RuntimeError as error:
        reporter.report(
            error,
            logger=create_autospec(logging.Logger, instance=True),
            source="Application",
            operation="run",
        )

    description = discord.post_error.call_args.args[0]["embeds"][0]["description"]
    assert len(description) < 4096
    assert "traceback truncated" in description
