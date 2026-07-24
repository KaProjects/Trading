from unittest.mock import MagicMock, create_autospec, patch

import pytest
from pydantic import ValidationError
from schedule import Scheduler

from cmc.retriever import BtcFearAndGreedRetrieverRunner
from config import AppConfig
from error_reporting import ErrorReporter
from gemini.retriever import StockDataRetrieverRunner
from main import (
    Application,
    create_app,
    create_discord_client,
    create_error_reporter,
)
from myfinnhub.retriever import FinnhubEarningsRetrieverRunner


def make_config(**overrides):
    data = {
        "firebase": "https://example.firebaseio.com",
        "cmc_api_key": "cmc",
        "discord_bot_token": "discord-token",
        "discord_guild_id": "guild-id",
        "discord_errorlog_channel_id": "error-channel-id",
        "finnhub_api_key": "finnhub",
        "gemini_api_key": "gemini",
    }
    data.update(overrides)
    return AppConfig.model_validate(data)


def make_application():
    errors = create_autospec(ErrorReporter, instance=True)
    return Application(
        btc_runner=create_autospec(
            BtcFearAndGreedRetrieverRunner,
            instance=True,
        ),
        finnhub_runner=create_autospec(
            FinnhubEarningsRetrieverRunner,
            instance=True,
        ),
        stock_runner=create_autospec(StockDataRetrieverRunner, instance=True),
        errors=errors,
        timezone="Europe/Prague",
        scheduler=Scheduler(),
    )


def test_application_configures_jobs_in_explicit_timezone():
    app = make_application()

    app.configure_jobs()
    app.configure_jobs()

    assert len(app.scheduler.jobs) == 3
    assert [job.at_time.strftime("%H:%M") for job in app.scheduler.jobs] == [
        "03:00",
        "07:00",
        "08:00",
    ]
    assert {
        job.at_time_zone.zone
        for job in app.scheduler.jobs
    } == {"Europe/Prague"}

    for job in app.scheduler.jobs:
        job.job_func()

    app.btc_runner.run.assert_called_once()
    app.finnhub_runner.run.assert_called_once()
    app.stock_runner.run.assert_called_once()


@pytest.mark.parametrize(
    "runner_type",
    [
        BtcFearAndGreedRetrieverRunner,
        FinnhubEarningsRetrieverRunner,
        StockDataRetrieverRunner,
    ],
)
def test_runner_logger_uses_runner_name(runner_type):
    assert runner_type.log.name == runner_type.name


def test_create_app_initializes_dependencies_from_validated_config():
    config = make_config(timezone="UTC", poll_interval_seconds=5)
    errors = create_autospec(ErrorReporter, instance=True)
    discord = MagicMock()

    with (
        patch("main.utils.init_firebase", autospec=True) as init_firebase,
        patch(
            "main.BtcFearAndGreedRetrieverRunner",
            autospec=True,
        ) as btc_runner,
        patch("main.FinnhubEarningsRetrieverRunner", autospec=True) as finnhub_runner,
        patch("main.StockDataRetrieverRunner", autospec=True) as stock_runner,
    ):
        app = create_app(
            config,
            discord=discord,
            error_reporter=errors,
        )

    init_firebase.assert_called_once_with("https://example.firebaseio.com")
    btc_runner.assert_called_once_with(
        cmc_api_key="cmc",
        discord=discord,
        error_reporter=errors,
    )
    finnhub_runner.assert_called_once_with(
        finnhub_api_key="finnhub",
        discord=discord,
        error_reporter=errors,
    )
    stock_runner.assert_called_once_with(
        gemini_api_key="gemini",
        discord=discord,
        error_reporter=errors,
    )
    assert app.errors is errors
    assert app.timezone == "UTC"
    assert app.poll_interval_seconds == 5


def test_create_discord_client_uses_bot_configuration():
    config = make_config()
    discord = MagicMock()

    with patch(
        "main.DiscordClient",
        autospec=True,
        return_value=discord,
    ) as discord_client:
        result = create_discord_client(config)

    discord_client.assert_called_once_with(
        bot_token="discord-token",
        guild_id="guild-id",
        error_channel_id="error-channel-id",
    )
    assert result is discord


def test_create_error_reporter_uses_shared_discord_client():
    config = make_config()
    discord = MagicMock()

    errors = create_error_reporter(config, discord=discord)

    assert errors.discord is discord
    assert errors.environment == "production"


def test_config_rejects_unknown_timezone_and_fields():
    with pytest.raises(ValidationError, match="Unknown timezone"):
        make_config(timezone="Mars/Olympus")

    with pytest.raises(ValidationError, match="Extra inputs are not permitted"):
        make_config(unexpected="value")


def test_run_pending_delegates_to_injected_scheduler():
    app = make_application()
    scheduler = MagicMock(spec=Scheduler)
    app.scheduler = scheduler

    app.run_pending()

    scheduler.run_pending.assert_called_once_with()


def test_run_forever_uses_injected_sleeper():
    class StopLoop(Exception):
        pass

    app = make_application()
    app.scheduler = MagicMock(spec=Scheduler)
    app.sleeper = MagicMock(side_effect=StopLoop)
    app._jobs_configured = True

    with pytest.raises(StopLoop):
        app.run_forever()

    app.scheduler.run_pending.assert_called_once_with()
    app.sleeper.assert_called_once_with(60)
