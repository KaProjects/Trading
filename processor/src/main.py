import logging
import time
from collections.abc import Callable
from dataclasses import dataclass, field

from schedule import Scheduler

import utils
from cmc.retriever import BtcFearAndGreedRetrieverRunner
from config import AppConfig
from gemini.retriever import StockDataRetrieverRunner
from myfinnhub.retriever import FinnhubEarningsRetrieverRunner

logger = logging.getLogger(__name__)


@dataclass
class Application:
    btc_runner: BtcFearAndGreedRetrieverRunner
    finnhub_runner: FinnhubEarningsRetrieverRunner
    stock_runner: StockDataRetrieverRunner
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


def create_app(config: AppConfig) -> Application:
    utils.init_firebase(config.firebase)
    return Application(
        btc_runner=BtcFearAndGreedRetrieverRunner(
            config.discord_btc_webhook_key.get_secret_value(),
            config.cmc_api_key.get_secret_value(),
        ),
        finnhub_runner=FinnhubEarningsRetrieverRunner(
            config.finnhub_api_key.get_secret_value(),
            config.discord_eventlog_webhook_key.get_secret_value(),
        ),
        stock_runner=StockDataRetrieverRunner(
            config.gemini_api_key.get_secret_value(),
            config.discord_earnings_webhook_key.get_secret_value(),
        ),
        timezone=config.timezone,
        poll_interval_seconds=float(config.poll_interval_seconds),
    )


def load_config(path: str = "envs.json") -> AppConfig:
    return AppConfig.model_validate(utils.parse(path))


def main() -> None:
    utils.configure_logging(logging.INFO)
    app = create_app(load_config())
    logger.info("Starting scheduler in timezone %s", app.timezone)
    app.run_forever()


if __name__ == "__main__":
    main()
