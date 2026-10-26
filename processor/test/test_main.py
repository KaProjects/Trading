from datetime import datetime
from unittest.mock import MagicMock, create_autospec, patch

import pytest
from pydantic import ValidationError
from schedule import Scheduler

from cmc.retriever import BtcFearAndGreedRetrieverRunner
from company_onboarding import CompanyOnboardingWatcher
from config import AppConfig
from error_reporting import ErrorReporter
from gemini.retriever import StockDataRetrieverRunner
from main import (
    Application,
    create_app,
    create_discord_client,
    create_error_reporter,
)
from gemini.ads_monitor import AdsRatioMonitorRunner
from myfinnhub.beta_retriever import FinnhubBetaRetrieverRunner
from myfinnhub.retriever import FinnhubEarningsRetrieverRunner
from polygon.prices_retriever import PolygonPricesRetrieverRunner
from polygon.retriever import PolygonNewsRetrieverRunner


def make_config(**overrides):
    data = {
        "firebase": "https://example.firebaseio.com",
        "cmc_api_key": "cmc",
        "discord_bot_token": "discord-token",
        "discord_guild_id": "guild-id",
        "discord_btc_webhook_key": "btc-webhook",
        "discord_eventlog_webhook_key": "eventlog-webhook",
        "discord_earnings_webhook_key": "earnings-webhook",
        "discord_errorlog_webhook_key": "errorlog-webhook",
        "finnhub_api_key": "finnhub",
        "gemini_api_key": "gemini",
        "polygon_api_key": "polygon",
        "sec_contact_email": "sec@example.com",
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
        polygon_prices_runner=create_autospec(
            PolygonPricesRetrieverRunner,
            instance=True,
        ),
        finnhub_runner=create_autospec(
            FinnhubEarningsRetrieverRunner,
            instance=True,
        ),
        finnhub_beta_runner=create_autospec(
            FinnhubBetaRetrieverRunner,
            instance=True,
        ),
        ads_ratio_runner=create_autospec(AdsRatioMonitorRunner, instance=True),
        stock_runner=create_autospec(StockDataRetrieverRunner, instance=True),
        polygon_news_runner=create_autospec(
            PolygonNewsRetrieverRunner,
            instance=True,
        ),
        onboarding_watcher=create_autospec(
            CompanyOnboardingWatcher,
            instance=True,
        ),
        errors=errors,
        timezone="Europe/Prague",
        scheduler=Scheduler(),
        clock=lambda tz: datetime(2026, 10, 21, 7, 45, tzinfo=tz),  # a Wednesday
    )


def test_application_configures_jobs_in_explicit_timezone():
    app = make_application()

    app.configure_jobs()
    app.configure_jobs()

    assert len(app.scheduler.jobs) == 5
    assert [job.at_time.strftime("%H:%M") for job in app.scheduler.jobs] == [
        "03:00",
        "07:30",
        "07:45",
        "08:00",
        "09:00",
    ]
    assert {
        job.at_time_zone.zone
        for job in app.scheduler.jobs
    } == {"Europe/Prague"}

    for job in app.scheduler.jobs:
        job.job_func()

    app.btc_runner.run.assert_called_once()
    app.polygon_prices_runner.run.assert_called_once()
    app.finnhub_runner.run.assert_called_once()
    app.finnhub_beta_runner.run.assert_not_called()  # Sundays only
    app.stock_runner.run.assert_called_once()
    app.polygon_news_runner.run.assert_called_once()
    assert app.scheduler.jobs[-1].start_day == "sunday"


def test_earnings_run_on_weekdays_and_betas_on_sundays():
    app = make_application()
    # Monday 19 October .. Sunday 25 October 2026
    expected = {
        19: (1, 0),
        20: (1, 0),
        21: (1, 0),
        22: (1, 0),
        23: (1, 0),
        24: (0, 0),  # Saturday: nothing
        25: (0, 1),  # Sunday: betas and the ADS ratio check
    }
    for day, (earnings, betas) in expected.items():
        app.clock = lambda tz, day=day: datetime(2026, 10, day, 7, 45, tzinfo=tz)
        app.finnhub_runner.reset_mock()
        app.finnhub_beta_runner.reset_mock()
        app.ads_ratio_runner.reset_mock()

        app.run_finnhub_jobs()

        assert app.finnhub_runner.run.call_count == earnings, day
        assert app.finnhub_beta_runner.run.call_count == betas, day
        assert app.ads_ratio_runner.run.call_count == betas, day


def test_sunday_is_judged_in_the_application_timezone():
    app = make_application()
    seen = []
    app.clock = lambda tz: (
        seen.append(tz.key)
        or datetime(2026, 10, 25, 7, 45, tzinfo=tz)
    )

    app.run_finnhub_jobs()

    assert seen == ["Europe/Prague"]


def test_a_failing_earnings_cycle_is_reported_and_not_raised():
    app = make_application()
    app.finnhub_runner.run.side_effect = RuntimeError("earnings blew up")

    app.run_finnhub_jobs()

    app.errors.report.assert_called_once()
    assert app.errors.report.call_args.kwargs["operation"] == (
        "run_finnhub_jobs"
    )
    assert app.errors.report.call_args.kwargs["context"] == {
        "runner": app.finnhub_runner.name,
    }


def test_a_failing_beta_cycle_does_not_skip_the_ads_ratio_check():
    app = make_application()
    app.clock = lambda tz: datetime(2026, 10, 25, 7, 45, tzinfo=tz)
    app.finnhub_beta_runner.run.side_effect = RuntimeError("beta blew up")

    app.run_finnhub_jobs()

    app.ads_ratio_runner.run.assert_called_once()
    app.errors.report.assert_called_once()


def test_a_failing_beta_cycle_is_reported_and_not_raised():
    app = make_application()
    app.clock = lambda tz: datetime(2026, 10, 25, 7, 45, tzinfo=tz)
    app.finnhub_beta_runner.run.side_effect = RuntimeError("beta blew up")

    app.run_finnhub_jobs()

    app.errors.report.assert_called_once()
    assert app.errors.report.call_args.kwargs["context"] == {
        "runner": app.finnhub_beta_runner.name,
    }


@pytest.mark.parametrize(
    "runner_type",
    [
        BtcFearAndGreedRetrieverRunner,
        FinnhubEarningsRetrieverRunner,
        FinnhubBetaRetrieverRunner,
        AdsRatioMonitorRunner,
        StockDataRetrieverRunner,
        PolygonNewsRetrieverRunner,
        PolygonPricesRetrieverRunner,
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
        patch(
            "main.FinnhubBetaRetrieverRunner",
            autospec=True,
        ) as finnhub_beta_runner,
        patch("main.StockDataRetrieverRunner", autospec=True) as stock_runner,
        patch("main.PolygonClient", autospec=True) as polygon_client,
        patch("main.SecClient", autospec=True) as sec_client,
        patch("main.FinnhubClient", autospec=True) as finnhub_client,
        patch("main.AdsRatioDetector", autospec=True) as ads_detector,
        patch("main.AdsRatioMonitorRunner", autospec=True) as ads_monitor,
        patch("main.QuarterPriceRanges", autospec=True) as price_ranges,
        patch("main.FrankfurterClient", autospec=True) as frankfurter_client,
        patch(
            "main.QuarterCurrencyConverter",
            autospec=True,
        ) as currency_converter,
        patch(
            "main.PolygonNewsRetrieverRunner",
            autospec=True,
        ) as polygon_news_runner,
        patch(
            "main.PolygonPricesRetrieverRunner",
            autospec=True,
        ) as polygon_prices_runner,
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
        currency_converter=currency_converter.return_value,
    )
    finnhub_beta_runner.assert_called_once_with(
        finnhub_api_key="finnhub",
        error_reporter=errors,
    )
    # One converter, shared by the quarter reports and Finnhub estimates.
    polygon_client.assert_called_once_with("polygon")
    currency_converter.assert_called_once_with(
        polygon_client.return_value,
        frankfurter_client.return_value,
    )
    stock_runner.assert_called_once_with(
        gemini_api_key="gemini",
        discord=discord,
        error_reporter=errors,
        currency_converter=currency_converter.return_value,
        sec_client=sec_client.return_value,
        price_ranges=price_ranges.return_value,
        ads_detector=ads_detector.return_value,
    )
    finnhub_client.assert_called_once_with("finnhub")
    ads_detector.assert_called_once_with(
        polygon_client.return_value,
        finnhub_client.return_value,
    )
    ads_monitor.assert_called_once_with(
        ads_detector.return_value,
        error_reporter=errors,
    )
    sec_client.assert_called_once_with("sec@example.com")
    price_ranges.assert_called_once_with(polygon_client.return_value)
    polygon_news_runner.assert_called_once_with(
        polygon_api_key="polygon",
        gemini_api_key="gemini",
        discord=discord,
        error_reporter=errors,
    )
    polygon_prices_runner.assert_called_once_with(
        polygon_api_key="polygon",
        error_reporter=errors,
    )
    assert app.errors is errors
    assert app.timezone == "UTC"
    assert app.poll_interval_seconds == 5
    assert app.onboarding_watcher.gemini is app.stock_runner
    assert app.onboarding_watcher.finnhub is app.finnhub_runner
    assert app.onboarding_watcher.finnhub_beta is app.finnhub_beta_runner
    assert app.onboarding_watcher.polygon_news is app.polygon_news_runner
    assert app.onboarding_watcher.discord is discord
    assert app.onboarding_watcher.errors is errors


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
        btc_webhook_key="btc-webhook",
        eventlog_webhook_key="eventlog-webhook",
        earnings_webhook_key="earnings-webhook",
        errorlog_webhook_key="errorlog-webhook",
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

    app.onboarding_watcher.start.assert_called_once_with()
    app.scheduler.run_pending.assert_called_once_with()
    app.sleeper.assert_called_once_with(60)
