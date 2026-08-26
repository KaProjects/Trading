import argparse
import logging
import sys
from collections.abc import Sequence
from typing import Protocol

import utils
from cmc.client import CoinMarketCapClient
from cmc.retriever import BtcFearAndGreedRetrieverRunner
from config import AppConfig
from dev.firebase_snapshot import ensure_firebase_snapshot
from dev.fakes import (
    ConsoleDiscordClient,
    FakeCoinMarketCapClient,
    FakeFinnhubClient,
    FakeFinnhubFirebaseService,
    FakeGeminiClient,
    FakeGeminiFirebaseService,
    FakePolygonClient,
    FakePolygonFirebaseService,
)
from discord.client import DiscordClient
from error_reporting import ErrorReporter
from gemini.client import GeminiClient
from gemini.retriever import StockDataRetrieverRunner
from gemini.service import FirebaseService as GeminiFirebaseService
from myfinnhub.client import FinnhubClient
from myfinnhub.retriever import FinnhubEarningsRetrieverRunner
from myfinnhub.service import FirebaseService as FinnhubFirebaseService
from polygon.client import PolygonClient
from polygon.retriever import PolygonNewsRetrieverRunner
from polygon.service import FirebaseService as PolygonFirebaseService

logger = logging.getLogger(__name__)

RUNNERS = {
    "btc": "BTC fear-and-greed retrieval and Discord notification",
    "gemini": "Gemini quarterly stock-data retrieval",
    "finnhub": "Finnhub earnings retrieval",
    "polygon": "Polygon company-news sentiment analysis",
}
CLIENTS = {
    "cmc": "CoinMarketCap",
    "gemini": "Gemini",
    "finnhub": "Finnhub",
    "polygon": "Polygon",
    "discord": "Discord",
    "firebase": "Firebase",
}
RUNNER_CLIENTS = {
    "btc": ("cmc", "discord"),
    "gemini": ("gemini", "discord", "firebase"),
    "finnhub": ("finnhub", "discord", "firebase"),
    "polygon": ("polygon", "gemini", "discord", "firebase"),
}


class Runner(Protocol):
    def run(self) -> None:
        pass


def create_parser() -> argparse.ArgumentParser:
    runner_list = "\n".join(
        f"  {name:<8} {description}"
        for name, description in RUNNERS.items()
    )
    parser = argparse.ArgumentParser(
        description=(
            "Run exactly one application runner immediately with independently "
            "selectable fake or production integrations."
        ),
        epilog=f"Available runners:\n{runner_list}",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "runner",
        nargs="?",
        choices=RUNNERS,
        help="runner to execute once",
    )
    for client, label in CLIENTS.items():
        parser.add_argument(
            f"--{client}-prod",
            action="store_true",
            help=f"use the production {label} client (default: fake)",
        )
    parser.add_argument(
        "--config",
        default="envs.json",
        help="production configuration file (default: envs.json)",
    )
    parser.add_argument(
        "--certificate",
        default="cert.json",
        help="Firebase certificate file (default: cert.json)",
    )
    return parser


def parse_args(
    argv: Sequence[str] | None = None,
) -> argparse.Namespace:
    parser = create_parser()
    args = parser.parse_args(argv)
    if args.runner is None:
        parser.print_help(sys.stderr)
        parser.exit(
            2,
            "\nerror: runner is required; available runners: "
            + ", ".join(RUNNERS)
            + "\n",
        )
    return args


def load_config(path: str) -> AppConfig:
    return AppConfig.model_validate(utils.parse(path))


def needs_production_config(args: argparse.Namespace) -> bool:
    return any(
        getattr(args, f"{client}_prod")
        for client in RUNNER_CLIENTS[args.runner]
    )


def build_runner(
    args: argparse.Namespace,
    config: AppConfig | None,
    firebase_snapshot: dict[str, object],
    error_reporter: ErrorReporter | None = None,
) -> Runner:
    errors = error_reporter or ErrorReporter(environment="development")
    if args.runner == "btc":
        return _build_btc_runner(args, config, errors)
    if args.runner == "gemini":
        return _build_gemini_runner(
            args,
            config,
            firebase_snapshot,
            errors,
        )
    if args.runner == "finnhub":
        return _build_finnhub_runner(
            args,
            config,
            firebase_snapshot,
            errors,
        )
    if args.runner == "polygon":
        return _build_polygon_runner(
            args,
            config,
            firebase_snapshot,
            errors,
        )
    raise ValueError(f"Unknown runner: {args.runner}")


def _build_btc_runner(
    args: argparse.Namespace,
    config: AppConfig | None,
    error_reporter: ErrorReporter,
) -> BtcFearAndGreedRetrieverRunner:
    if args.cmc_prod:
        production_config = _require_config(config)
        cmc_client = CoinMarketCapClient(
            production_config.cmc_api_key.get_secret_value()
        )
    else:
        cmc_client = FakeCoinMarketCapClient()

    discord = _build_discord_client(
        args,
        config,
    )
    return BtcFearAndGreedRetrieverRunner(
        client=cmc_client,
        discord=discord,
        error_reporter=error_reporter,
    )


def _build_gemini_runner(
    args: argparse.Namespace,
    config: AppConfig | None,
    firebase_snapshot: dict[str, object],
    error_reporter: ErrorReporter,
) -> StockDataRetrieverRunner:
    if args.gemini_prod:
        production_config = _require_config(config)
        gemini_client = GeminiClient(
            api_key=production_config.gemini_api_key.get_secret_value(),
            model=StockDataRetrieverRunner.model,
        )
    else:
        gemini_client = FakeGeminiClient()

    service = _build_firebase_service(
        args,
        config,
        production_factory=GeminiFirebaseService,
        fake_factory=FakeGeminiFirebaseService,
        firebase_snapshot=firebase_snapshot,
        error_reporter=error_reporter,
    )
    discord = _build_discord_client(
        args,
        config,
    )
    return StockDataRetrieverRunner(
        client=gemini_client,
        service=service,
        discord=discord,
        error_reporter=error_reporter,
    )


def _build_finnhub_runner(
    args: argparse.Namespace,
    config: AppConfig | None,
    firebase_snapshot: dict[str, object],
    error_reporter: ErrorReporter,
) -> FinnhubEarningsRetrieverRunner:
    if args.finnhub_prod:
        production_config = _require_config(config)
        finnhub_client = FinnhubClient(
            api_key=production_config.finnhub_api_key.get_secret_value()
        )
    else:
        finnhub_client = FakeFinnhubClient()

    service = _build_firebase_service(
        args,
        config,
        production_factory=FinnhubFirebaseService,
        fake_factory=FakeFinnhubFirebaseService,
        firebase_snapshot=firebase_snapshot,
        error_reporter=error_reporter,
    )
    discord = _build_discord_client(
        args,
        config,
    )
    return FinnhubEarningsRetrieverRunner(
        client=finnhub_client,
        service=service,
        discord=discord,
        error_reporter=error_reporter,
        sleeper=lambda _: None,
    )


def _build_polygon_runner(
    args: argparse.Namespace,
    config: AppConfig | None,
    firebase_snapshot: dict[str, object],
    error_reporter: ErrorReporter,
) -> PolygonNewsRetrieverRunner:
    if args.polygon_prod:
        production_config = _require_config(config)
        polygon_client = PolygonClient(
            api_key=(
                production_config.polygon_api_key.get_secret_value()
            )
        )
    else:
        polygon_client = FakePolygonClient(list(firebase_snapshot))

    if args.gemini_prod:
        production_config = _require_config(config)
        gemini_client = GeminiClient(
            api_key=production_config.gemini_api_key.get_secret_value(),
            model=PolygonNewsRetrieverRunner.model,
        )
    else:
        gemini_client = FakeGeminiClient()

    service = _build_firebase_service(
        args,
        config,
        production_factory=PolygonFirebaseService,
        fake_factory=FakePolygonFirebaseService,
        firebase_snapshot=firebase_snapshot,
        error_reporter=error_reporter,
    )
    discord = _build_discord_client(args, config)
    return PolygonNewsRetrieverRunner(
        client=polygon_client,
        gemini=gemini_client,
        service=service,
        discord=discord,
        error_reporter=error_reporter,
    )


def _build_discord_client(
    args: argparse.Namespace,
    config: AppConfig | None,
) -> DiscordClient | ConsoleDiscordClient:
    if not args.discord_prod:
        return ConsoleDiscordClient()
    production_config = _require_config(config)
    return DiscordClient(
        bot_token=production_config.discord_bot_token.get_secret_value(),
        guild_id=production_config.discord_guild_id,
        btc_webhook_key=(
            production_config.discord_btc_webhook_key.get_secret_value()
        ),
        eventlog_webhook_key=(
            production_config.discord_eventlog_webhook_key.get_secret_value()
        ),
        earnings_webhook_key=(
            production_config.discord_earnings_webhook_key.get_secret_value()
        ),
        errorlog_webhook_key=(
            production_config.discord_errorlog_webhook_key.get_secret_value()
        ),
    )


def _build_firebase_service(
    args: argparse.Namespace,
    config: AppConfig | None,
    *,
    production_factory,
    fake_factory,
    firebase_snapshot: dict[str, object],
    error_reporter: ErrorReporter,
):
    if not args.firebase_prod:
        return fake_factory(
            firebase_snapshot,
            error_reporter=error_reporter,
        )
    production_config = _require_config(config)
    utils.init_firebase(production_config.firebase)
    return production_factory(error_reporter=error_reporter)


def _require_config(config: AppConfig | None) -> AppConfig:
    if config is None:
        raise RuntimeError("Production client selected without configuration")
    return config


def create_error_reporter(config: AppConfig) -> ErrorReporter:
    return ErrorReporter(
        DiscordClient(
            bot_token=config.discord_bot_token.get_secret_value(),
            guild_id=config.discord_guild_id,
            btc_webhook_key=(
                config.discord_btc_webhook_key.get_secret_value()
            ),
            eventlog_webhook_key=(
                config.discord_eventlog_webhook_key.get_secret_value()
            ),
            earnings_webhook_key=(
                config.discord_earnings_webhook_key.get_secret_value()
            ),
            errorlog_webhook_key=(
                config.discord_errorlog_webhook_key.get_secret_value()
            ),
        ),
        environment="development",
    )


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    utils.configure_logging(logging.INFO)
    config = load_config(args.config)
    errors = create_error_reporter(config)
    try:
        firebase_snapshot = ensure_firebase_snapshot(
            config_path=args.config,
            certificate_path=args.certificate,
        )
        selected_modes = ", ".join(
        (
            f"{client}="
            f"{'production' if getattr(args, f'{client}_prod') else 'fake'}"
        )
            for client in RUNNER_CLIENTS[args.runner]
        )
        logger.info(
            "Running %s once with %s",
            args.runner,
            selected_modes,
        )
        runner = build_runner(
            args,
            config if needs_production_config(args) else None,
            firebase_snapshot,
            error_reporter=errors,
        )
        runner.run()
    except Exception as exception:
        errors.report(
            exception,
            logger=logger,
            source="DevelopmentApplication",
            operation="run_once",
            context={"runner": args.runner},
        )
        raise
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
