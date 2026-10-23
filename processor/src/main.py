import logging
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime, tzinfo
from zoneinfo import ZoneInfo

from schedule import Scheduler

import utils
from cmc.retriever import BtcFearAndGreedRetrieverRunner
from company_onboarding import CompanyOnboardingWatcher
from config import AppConfig
from discord.client import DiscordClient
from error_reporting import ErrorReporter
from frankfurter.client import FrankfurterClient
from gemini.currency import QuarterCurrencyConverter
from gemini.retriever import StockDataRetrieverRunner
from myfinnhub.beta_retriever import FinnhubBetaRetrieverRunner
from myfinnhub.retriever import FinnhubEarningsRetrieverRunner
from polygon.client import PolygonClient
from polygon.prices_retriever import PolygonPricesRetrieverRunner
from polygon.retriever import PolygonNewsRetrieverRunner

logger = logging.getLogger(__name__)
SATURDAY = 5
SUNDAY = 6


@dataclass
class Application:
    btc_runner: BtcFearAndGreedRetrieverRunner
    polygon_prices_runner: PolygonPricesRetrieverRunner
    finnhub_runner: FinnhubEarningsRetrieverRunner
    finnhub_beta_runner: FinnhubBetaRetrieverRunner
    stock_runner: StockDataRetrieverRunner
    polygon_news_runner: PolygonNewsRetrieverRunner
    onboarding_watcher: CompanyOnboardingWatcher
    errors: ErrorReporter
    timezone: str
    poll_interval_seconds: float = 60
    scheduler: Scheduler = field(default_factory=Scheduler)
    sleeper: Callable[[float], None] = time.sleep
    clock: Callable[[tzinfo], datetime] = datetime.now
    _jobs_configured: bool = field(default=False, init=False)

    def configure_jobs(self) -> None:
        if self._jobs_configured:
            return

        self.scheduler.every().day.at("03:00", self.timezone).do(
            self.btc_runner.run
        )
        self.scheduler.every().day.at("07:30", self.timezone).do(
            self.polygon_prices_runner.run
        )
        self.scheduler.every().day.at("07:45", self.timezone).do(
            self.run_finnhub_jobs
        )
        self.scheduler.every().day.at("08:00", self.timezone).do(
            self.stock_runner.run
        )
        self.scheduler.every().sunday.at("09:00", self.timezone).do(
            self.polygon_news_runner.run
        )
        self._jobs_configured = True

    def run_finnhub_jobs(self) -> None:
        # One daily trigger, two cycles: the earnings on weekdays, then the
        # betas on Sundays. A failure in one never skips the other.
        weekday = self.clock(ZoneInfo(self.timezone)).weekday()
        runners = []
        if weekday < SATURDAY:
            runners.append(self.finnhub_runner)
        if weekday == SUNDAY:
            runners.append(self.finnhub_beta_runner)
        for runner in runners:
            try:
                runner.run()
            except Exception as exception:
                self.errors.report(
                    exception,
                    logger=logger,
                    source="Application",
                    operation="run_finnhub_jobs",
                    context={"runner": runner.name},
                )

    def run_pending(self) -> None:
        self.scheduler.run_pending()

    def run_forever(self) -> None:
        self.onboarding_watcher.start()
        self.configure_jobs()
        while True:
            self.run_pending()
            self.sleeper(self.poll_interval_seconds)


def create_error_reporter(
    config: AppConfig,
    *,
    discord: DiscordClient | None = None,
    environment: str = "production",
) -> ErrorReporter:
    return ErrorReporter(
        discord or create_discord_client(config),
        environment=environment,
    )


def create_discord_client(config: AppConfig) -> DiscordClient:
    return DiscordClient(
        bot_token=config.discord_bot_token.get_secret_value(),
        guild_id=config.discord_guild_id,
        btc_webhook_key=config.discord_btc_webhook_key.get_secret_value(),
        eventlog_webhook_key=(
            config.discord_eventlog_webhook_key.get_secret_value()
        ),
        earnings_webhook_key=(
            config.discord_earnings_webhook_key.get_secret_value()
        ),
        errorlog_webhook_key=(
            config.discord_errorlog_webhook_key.get_secret_value()
        ),
    )


def create_app(
    config: AppConfig,
    *,
    discord: DiscordClient | None = None,
    error_reporter: ErrorReporter | None = None,
) -> Application:
    discord = discord or create_discord_client(config)
    errors = error_reporter or create_error_reporter(
        config,
        discord=discord,
    )
    utils.init_firebase(config.firebase)
    currency_converter = QuarterCurrencyConverter(
        PolygonClient(config.polygon_api_key.get_secret_value()),
        FrankfurterClient(),
    )
    finnhub_runner = FinnhubEarningsRetrieverRunner(
        finnhub_api_key=config.finnhub_api_key.get_secret_value(),
        discord=discord,
        error_reporter=errors,
        currency_converter=currency_converter,
    )
    finnhub_beta_runner = FinnhubBetaRetrieverRunner(
        finnhub_api_key=config.finnhub_api_key.get_secret_value(),
        error_reporter=errors,
    )
    stock_runner = StockDataRetrieverRunner(
        gemini_api_key=config.gemini_api_key.get_secret_value(),
        discord=discord,
        error_reporter=errors,
        currency_converter=currency_converter,
    )
    polygon_news_runner = PolygonNewsRetrieverRunner(
        polygon_api_key=config.polygon_api_key.get_secret_value(),
        gemini_api_key=config.gemini_api_key.get_secret_value(),
        discord=discord,
        error_reporter=errors,
    )
    polygon_prices_runner = PolygonPricesRetrieverRunner(
        polygon_api_key=config.polygon_api_key.get_secret_value(),
        error_reporter=errors,
    )
    return Application(
        btc_runner=BtcFearAndGreedRetrieverRunner(
            cmc_api_key=config.cmc_api_key.get_secret_value(),
            discord=discord,
            error_reporter=errors,
        ),
        polygon_prices_runner=polygon_prices_runner,
        finnhub_runner=finnhub_runner,
        finnhub_beta_runner=finnhub_beta_runner,
        stock_runner=stock_runner,
        polygon_news_runner=polygon_news_runner,
        onboarding_watcher=CompanyOnboardingWatcher(
            gemini=stock_runner,
            finnhub=finnhub_runner,
            finnhub_beta=finnhub_beta_runner,
            polygon_news=polygon_news_runner,
            discord=discord,
            error_reporter=errors,
        ),
        errors=errors,
        timezone=config.timezone,
        poll_interval_seconds=float(config.poll_interval_seconds),
    )


def load_config(path: str = "envs.json") -> AppConfig:
    return AppConfig.model_validate(utils.parse(path))


def main() -> None:
    utils.configure_logging(logging.INFO)
    config = load_config()
    discord = create_discord_client(config)
    errors = create_error_reporter(config, discord=discord)
    try:
        app = create_app(
            config,
            discord=discord,
            error_reporter=errors,
        )
        logger.info("Starting scheduler in timezone %s", app.timezone)
        app.run_forever()
    except Exception as exception:
        errors.report(
            exception,
            logger=logger,
            source="Application",
            operation="startup_or_scheduler",
        )
        raise


if __name__ == "__main__":
    main()
