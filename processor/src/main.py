import logging
import time
from collections.abc import Callable
from dataclasses import dataclass, field

from schedule import Scheduler

import utils
from cmc.retriever import BtcFearAndGreedRetrieverRunner
from config import AppConfig
from discord.client import DiscordClient
from error_reporting import ErrorReporter
from gemini.retriever import StockDataRetrieverRunner
from myfinnhub.retriever import FinnhubEarningsRetrieverRunner

logger = logging.getLogger(__name__)


@dataclass
class Application:
    btc_runner: BtcFearAndGreedRetrieverRunner
    finnhub_runner: FinnhubEarningsRetrieverRunner
    stock_runner: StockDataRetrieverRunner
    errors: ErrorReporter
    timezone: str
    poll_interval_seconds: float = 60
    scheduler: Scheduler = field(default_factory=Scheduler)
    sleeper: Callable[[float], None] = time.sleep
    _jobs_configured: bool = field(default=False, init=False)

    def configure_jobs(self) -> None:
        if self._jobs_configured:
            return

        self.scheduler.every().day.at("03:00", self.timezone).do(
            self.btc_runner.run
        )
        self.scheduler.every().day.at("07:00", self.timezone).do(
            self.finnhub_runner.run
        )
        self.scheduler.every().day.at("08:00", self.timezone).do(
            self.stock_runner.run
        )
        self._jobs_configured = True

    def run_pending(self) -> None:
        self.scheduler.run_pending()

    def run_forever(self) -> None:
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
        error_channel_id=config.discord_errorlog_channel_id,
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
    return Application(
        btc_runner=BtcFearAndGreedRetrieverRunner(
            cmc_api_key=config.cmc_api_key.get_secret_value(),
            discord=discord,
            error_reporter=errors,
        ),
        finnhub_runner=FinnhubEarningsRetrieverRunner(
            finnhub_api_key=config.finnhub_api_key.get_secret_value(),
            discord=discord,
            error_reporter=errors,
        ),
        stock_runner=StockDataRetrieverRunner(
            gemini_api_key=config.gemini_api_key.get_secret_value(),
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
