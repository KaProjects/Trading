import logging
from decimal import Decimal
from unittest.mock import call, create_autospec, patch

import pytest

from discord.client import DiscordClient
from myfinnhub.client import FinnhubClient
from myfinnhub.retriever import FinnhubEarningsRetrieverRunner
from myfinnhub.models import Company, Earnings, Quarter
from myfinnhub.service import FirebaseService


def make_earnings(
    report="2026-04-27-bmo",
    epse=1.0,
    reve=1_000_000,
    epsa=None,
    reva=None,
):
    return Earnings(report=report, epse=epse, reve=reve, epsa=epsa, reva=reva)


def make_company(**quarters):
    return Company(root={
        quarter_id: Quarter(root=history)
        for quarter_id, history in quarters.items()
    })


class TestFinnhubEarningsRetriever:
    @pytest.fixture
    def runner(self):
        with patch("myfinnhub.retriever.time.sleep", autospec=True):
            instance = object.__new__(FinnhubEarningsRetrieverRunner)
            instance.service = create_autospec(FirebaseService, instance=True)
            instance.client = create_autospec(FinnhubClient, instance=True)
            instance.discord = create_autospec(DiscordClient, instance=True)
            instance.log = create_autospec(logging.Logger, instance=True)
            instance.discord_post_earnings = create_autospec(instance.discord_post_earnings)
            instance.almost_equals_earnings = create_autospec(instance.almost_equals_earnings)
            yield instance

    def test_init_new_company_earnings(self, runner):
        """Test Case: Company exists in DB but is None (first time fetch)."""
        runner.service.get_companies.return_value = {"AAPL": None}
        mock_earnings = {"26Q1": make_earnings()}
        runner.client.get_earnings.return_value = mock_earnings
        runner.run()
        runner.service.init_company.assert_called_once_with("AAPL", mock_earnings)
        runner.discord_post_earnings.assert_called_once()

    def test_add_new_quarter_to_existing_company(self, runner):
        """Test Case: Company exists, but a new quarter appears in the API."""
        stored_q1 = make_earnings(report="2026-01-01-bmo")
        mock_company = make_company(**{"26Q1": {"20260101": stored_q1}})
        runner.service.get_companies.return_value = {"TSLA": mock_company}
        q1_earnings = make_earnings()
        q2_earnings = make_earnings(report="2026-07-27-bmo")
        earnings_dict = {"26Q1": q1_earnings, "26Q2": q2_earnings}
        runner.client.get_earnings.return_value = earnings_dict
        runner.almost_equals_earnings.return_value = True
        runner.run()
        runner.service.init_quarter.assert_called_once_with("TSLA", "26Q2", q2_earnings)
        runner.discord_post_earnings.assert_called_once_with("TSLA", "26Q2", None, q2_earnings)

    def test_update_existing_earnings_significant_change(self, runner):
        """Test Case: Quarter exists, but data changed (almost_equals_earnings is False)."""
        latest_in_db = make_earnings(epse=1.0)
        mock_company = make_company(**{"26Q1": {"20260101": latest_in_db}})
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        now_earnings = make_earnings(epse=1.5)
        runner.client.get_earnings.return_value = {"26Q1": now_earnings}
        runner.almost_equals_earnings.return_value = False
        runner.run()
        runner.service.new_earnings.assert_called_once_with("NVDA", "26Q1", now_earnings)
        runner.discord_post_earnings.assert_called_once_with("NVDA", "26Q1", latest_in_db, now_earnings)

    def test_no_change_logs_message(self, runner):
        """Test Case: Data is identical or 'almost equal' -> Log no change."""
        latest = make_earnings()
        mock_company = make_company(**{"26Q1": {"20260101": latest}})
        runner.service.get_companies.return_value = {"MSFT": mock_company}
        runner.client.get_earnings.return_value = {"26Q1": make_earnings()}
        runner.almost_equals_earnings.return_value = True
        runner.run()
        runner.service.new_earnings.assert_not_called()
        runner.log.info.assert_called_once()
        assert "no change" in runner.log.info.call_args[0][0].lower()

    def test_exception_per_company_does_not_stop_loop(self, runner):
        """Test Case: One company fails, but the loop continues for the next."""
        runner.service.get_companies.return_value = {
            "FAIL": Company(root={}),
            "SUCCESS": Company(root={}),
        }
        runner.client.get_earnings.side_effect = [Exception("API Error"), {}]
        runner.run()
        runner.log.error.assert_called_once()
        runner.log.exception.assert_called_once()
        actual_log = runner.log.error.call_args[0][0].lower()
        assert "error while processing fail" in actual_log
        assert runner.client.get_earnings.call_count == 2

    def test_empty_earnings_skips_company_actions(self, runner):
        """Test Case: Empty API response should skip all mutation and notification actions."""
        runner.service.get_companies.return_value = {"AAPL": Company(root={})}
        runner.client.get_earnings.return_value = {}
        runner.run()
        runner.service.init_company.assert_not_called()
        runner.service.init_quarter.assert_not_called()
        runner.service.new_earnings.assert_not_called()
        runner.discord_post_earnings.assert_not_called()
        runner.log.info.assert_not_called()
        runner.log.error.assert_not_called()
        runner.log.exception.assert_not_called()

    def test_run_logs_exception_when_loading_companies_fails(self, runner):
        """Test Case: Failure during company loading should be logged once and stop processing."""
        error = Exception("DB Error")
        runner.service.get_companies.side_effect = error
        runner.run()
        runner.client.get_earnings.assert_not_called()
        runner.log.exception.assert_called_once_with(error)
        runner.log.error.assert_not_called()

    def test_existing_company_handles_new_and_changed_quarters_same_run(self, runner):
        """Test Case: Existing company can add a new quarter and update an existing quarter in one run."""
        latest_in_db = make_earnings(epse=1.0)
        mock_company = make_company(**{"26Q1": {"20260101": latest_in_db}})
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        q1_earnings = make_earnings(epse=1.5)
        q2_earnings = make_earnings(report="2026-07-27-bmo")
        runner.client.get_earnings.return_value = {"26Q1": q1_earnings, "26Q2": q2_earnings}
        runner.almost_equals_earnings.return_value = False
        runner.run()
        runner.service.init_quarter.assert_called_once_with("NVDA", "26Q2", q2_earnings)
        runner.service.new_earnings.assert_called_once_with("NVDA", "26Q1", q1_earnings)
        runner.discord_post_earnings.assert_has_calls([
            call("NVDA", "26Q2", None, q2_earnings),
            call("NVDA", "26Q1", latest_in_db, q1_earnings),
        ])
        runner.log.info.assert_not_called()

    def test_init_new_company_posts_all_quarters_in_reverse_order(self, runner):
        """Test Case: New company should initialize once and post every quarter in reverse insertion order."""
        runner.service.get_companies.return_value = {"AAPL": None}
        q1_earnings = make_earnings(report="2026-04-27-bmo")
        q2_earnings = make_earnings(report="2026-07-27-bmo")
        q3_earnings = make_earnings(report="2026-10-27-bmo")
        earnings_dict = {
            "26Q1": q1_earnings,
            "26Q2": q2_earnings,
            "26Q3": q3_earnings,
        }
        runner.client.get_earnings.return_value = earnings_dict
        runner.run()
        runner.service.init_company.assert_called_once_with("AAPL", earnings_dict)
        assert runner.discord_post_earnings.call_args_list == [
            call("AAPL", "26Q3", None, q3_earnings),
            call("AAPL", "26Q2", None, q2_earnings),
            call("AAPL", "26Q1", None, q1_earnings),
        ]


def make_real_runner():
    runner = object.__new__(FinnhubEarningsRetrieverRunner)
    runner.discord = create_autospec(DiscordClient, instance=True)
    return runner


def test_discord_payload_preserves_zero_actual_values():
    runner = make_real_runner()
    earnings = make_earnings(epsa=0, reva=0)

    runner.discord_post_earnings("AAPL", "26Q1", None, earnings)

    payload = runner.discord.post.call_args.args[0]
    reported = payload["embeds"][0]["fields"][1]
    assert reported["name"] == "Reported:"
    assert "earnings: \u200b 0.00" in reported["value"]
    assert "revenues: \u200b 0.00M" in reported["value"]


def test_discord_payload_includes_partially_available_actuals():
    runner = make_real_runner()
    earnings = make_earnings(epsa=Decimal("0.25"), reva=None)

    runner.discord_post_earnings("AAPL", "26Q1", None, earnings)

    payload = runner.discord.post.call_args.args[0]
    reported = payload["embeds"][0]["fields"][1]
    assert "earnings: \u200b 0.25" in reported["value"]


@pytest.mark.parametrize(
    ("left", "right", "expected"),
    [
        (None, None, True),
        (None, Decimal("-1000"), False),
        ("invalid", "invalid", False),
        (Decimal("0"), Decimal("0.009"), True),
        (Decimal("0"), Decimal("0.02"), False),
        (Decimal("100"), Decimal("105"), True),
        (Decimal("100"), Decimal("106"), False),
    ],
)
def test_almost_equals_uses_explicit_numeric_semantics(left, right, expected):
    runner = make_real_runner()

    assert runner.almost_equals(left, right) is expected
