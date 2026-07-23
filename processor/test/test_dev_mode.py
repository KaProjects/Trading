import logging
import subprocess
from pathlib import Path
from unittest.mock import Mock, patch

import pytest

from cmc.retriever import BtcFearAndGreedRetrieverRunner
from config import AppConfig
from dev import data
from dev.fakes import (
    ConsoleDiscordClient,
    FakeCoinMarketCapClient,
    FakeFinnhubClient,
    FakeFinnhubFirebaseService,
    FakeGeminiClient,
    FakeGeminiFirebaseService,
)
from dev.run import build_runner, main, needs_production_config, parse_args
from gemini.retriever import StockDataRetrieverRunner
from myfinnhub.retriever import FinnhubEarningsRetrieverRunner


def test_missing_runner_prints_all_options_and_fails(capsys):
    with pytest.raises(SystemExit) as raised:
        parse_args([])

    assert raised.value.code == 2
    error = capsys.readouterr().err
    assert "Available runners:" in error
    assert "btc" in error
    assert "gemini" in error
    assert "finnhub" in error


def test_all_clients_default_to_fake():
    args = parse_args(["btc"])

    assert not args.cmc_production
    assert not args.gemini_production
    assert not args.finnhub_production
    assert not args.discord_production
    assert not args.firebase_production


@pytest.mark.parametrize(
    ("runner_name", "option", "attribute"),
    [
        ("btc", "--cmc-production", "cmc_production"),
        ("gemini", "--gemini-production", "gemini_production"),
        ("finnhub", "--finnhub-production", "finnhub_production"),
        ("btc", "--discord-production", "discord_production"),
        ("gemini", "--firebase-production", "firebase_production"),
    ],
)
def test_each_production_switch_requires_configuration(
    runner_name,
    option,
    attribute,
):
    args = parse_args([runner_name, option])

    assert getattr(args, attribute)
    assert needs_production_config(args)


@pytest.mark.parametrize(
    ("runner_name", "runner_type", "client_type", "service_type"),
    [
        (
            "btc",
            BtcFearAndGreedRetrieverRunner,
            FakeCoinMarketCapClient,
            None,
        ),
        (
            "gemini",
            StockDataRetrieverRunner,
            FakeGeminiClient,
            FakeGeminiFirebaseService,
        ),
        (
            "finnhub",
            FinnhubEarningsRetrieverRunner,
            FakeFinnhubClient,
            FakeFinnhubFirebaseService,
        ),
    ],
)
def test_build_runner_uses_only_fake_dependencies_by_default(
    runner_name,
    runner_type,
    client_type,
    service_type,
):
    runner = build_runner(
        parse_args([runner_name]),
        config=None,
        firebase_snapshot=data.firebase_company_snapshot(),
    )

    assert isinstance(runner, runner_type)
    assert isinstance(runner.client, client_type)
    assert isinstance(runner.discord, ConsoleDiscordClient)
    if service_type is not None:
        assert isinstance(runner.service, service_type)


@pytest.mark.parametrize("runner_name", ["btc", "gemini", "finnhub"])
def test_fake_runner_executes_end_to_end_without_network(
    runner_name,
    caplog,
):
    runner = build_runner(
        parse_args([runner_name]),
        config=None,
        firebase_snapshot=data.firebase_company_snapshot(),
    )

    with caplog.at_level(logging.INFO):
        runner.run()

    assert "FAKE GET" in caplog.text
    assert "FAKE POST Discord webhook" in caplog.text


def test_main_executes_exactly_one_runner_without_loading_config():
    runner = Mock()
    firebase_snapshot = data.firebase_company_snapshot()

    with (
        patch(
            "dev.run.ensure_firebase_snapshot",
            autospec=True,
            return_value=firebase_snapshot,
        ) as ensure_snapshot,
        patch("dev.run.load_config", autospec=True) as load_config,
        patch(
            "dev.run.build_runner",
            autospec=True,
            return_value=runner,
        ) as build,
    ):
        result = main(["btc"])

    assert result == 0
    ensure_snapshot.assert_called_once_with(
        config_path="envs.json",
        certificate_path="cert.json",
    )
    load_config.assert_not_called()
    build.assert_called_once()
    assert build.call_args.args[1] is None
    assert build.call_args.args[2] is firebase_snapshot
    runner.run.assert_called_once_with()


def test_production_switch_loads_selected_config():
    config = AppConfig.model_validate({
        "firebase": "https://example.firebaseio.com",
        "cmc_api_key": "cmc",
        "discord_btc_webhook_key": "discord-btc",
        "discord_eventlog_webhook_key": "discord-events",
        "discord_earnings_webhook_key": "discord-earnings",
        "finnhub_api_key": "finnhub",
        "gemini_api_key": "gemini",
    })
    runner = Mock()
    firebase_snapshot = data.firebase_company_snapshot()

    with (
        patch(
            "dev.run.ensure_firebase_snapshot",
            autospec=True,
            return_value=firebase_snapshot,
        ),
        patch(
            "dev.run.load_config",
            autospec=True,
            return_value=config,
        ) as load_config,
        patch(
            "dev.run.build_runner",
            autospec=True,
            return_value=runner,
        ) as build,
    ):
        result = main([
            "btc",
            "--cmc-production",
            "--config",
            "custom-envs.json",
        ])

    assert result == 0
    load_config.assert_called_once_with("custom-envs.json")
    assert build.call_args.args[1] is config
    runner.run.assert_called_once_with()


def test_run_dev_shell_hands_arguments_to_dev_app():
    project_dir = Path(__file__).resolve().parents[1]

    result = subprocess.run(
        [project_dir / "run-dev.sh", "--help"],
        cwd=project_dir,
        capture_output=True,
        text=True,
        check=False,
    )

    assert result.returncode == 0
    assert "Available runners:" in result.stdout
