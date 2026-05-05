from unittest.mock import MagicMock, patch, call

import pytest

from myfinnhub.earnings_retriever import FinnhubEarningsRetrieverRunner
from myfinnhub.models import Earnings, Company


class TestFinnhubEarningsRetriever:
    @pytest.fixture
    def runner(self):
        with patch('myfinnhub.earnings_retriever.FirebaseService') as mock_service, \
                patch('myfinnhub.earnings_retriever.FinnhubClient') as mock_client, \
                patch('myfinnhub.earnings_retriever.DiscordClient') as mock_discord, \
                patch('myfinnhub.earnings_retriever.time.sleep'):
            instance = FinnhubEarningsRetrieverRunner(finnhub_api_key="fake", discord_webhook_key="fake")
            instance.service = mock_service.return_value
            instance.client = mock_client.return_value
            instance.discord = mock_discord.return_value
            instance.log = MagicMock()
            instance.discord_post_earnings = MagicMock()
            instance.almost_equals_earnings = MagicMock()
            return instance

    def test_init_new_company_earnings(self, runner):
        """Test Case: Company exists in DB but is None (first time fetch)."""
        runner.service.get_companies.return_value = {"AAPL": None}
        mock_earnings = {"Q1": MagicMock(spec=Earnings)}
        runner.client.get_earnings.return_value = mock_earnings
        runner.run()
        runner.service.init_company.assert_called_once_with("AAPL", mock_earnings)
        runner.discord_post_earnings.assert_called_once()

    def test_add_new_quarter_to_existing_company(self, runner):
        """Test Case: Company exists, but a new quarter appears in the API."""
        mock_company = MagicMock(spec=Company)
        mock_company.root = {"Q1": MagicMock()}
        runner.service.get_companies.return_value = {"TSLA": mock_company}
        q1_earnings = MagicMock(spec=Earnings)
        q2_earnings = MagicMock(spec=Earnings)
        earnings_dict = {"Q1": q1_earnings, "Q2": q2_earnings}
        runner.client.get_earnings.return_value = earnings_dict
        runner.run()
        runner.service.init_quarter.assert_called_once_with("TSLA", "Q2", q2_earnings)
        runner.discord_post_earnings.assert_called_once_with("TSLA", "Q2", None, q2_earnings)

    def test_update_existing_earnings_significant_change(self, runner):
        """Test Case: Quarter exists, but data changed (almost_equals_earnings is False)."""
        latest_in_db = MagicMock(spec=Earnings)
        mock_quarter = MagicMock()
        mock_quarter.root = {"2026-01-01": latest_in_db}
        mock_company = MagicMock(spec=Company)
        mock_company.root = {"Q1": mock_quarter}
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        now_earnings = MagicMock(spec=Earnings)
        runner.client.get_earnings.return_value = {"Q1": now_earnings}
        runner.almost_equals_earnings.return_value = False
        runner.run()
        runner.service.new_earnings.assert_called_once_with("NVDA", "Q1", now_earnings)
        runner.discord_post_earnings.assert_called_once_with("NVDA", "Q1", latest_in_db, now_earnings)

    def test_no_change_logs_message(self, runner):
        """Test Case: Data is identical or 'almost equal' -> Log no change."""
        mock_quarter = MagicMock()
        mock_quarter.root = {"2026-01-01": MagicMock(spec=Earnings)}
        mock_company = MagicMock(spec=Company)
        mock_company.root = {"Q1": mock_quarter}
        runner.service.get_companies.return_value = {"MSFT": mock_company}
        runner.client.get_earnings.return_value = {"Q1": MagicMock(spec=Earnings)}
        runner.almost_equals_earnings.return_value = True
        runner.run()
        runner.service.new_earnings.assert_not_called()
        runner.log.info.assert_called_once()
        assert "no change" in runner.log.info.call_args[0][0].lower()

    def test_exception_per_company_does_not_stop_loop(self, runner):
        """Test Case: One company fails, but the loop continues for the next."""
        runner.service.get_companies.return_value = {"FAIL": MagicMock(), "SUCCESS": MagicMock()}
        runner.client.get_earnings.side_effect = [Exception("API Error"), {}]
        runner.run()
        runner.log.error.assert_called_once()
        runner.log.exception.assert_called_once()
        actual_log = runner.log.error.call_args[0][0].lower()
        assert "error while processing fail" in actual_log
        assert runner.client.get_earnings.call_count == 2

    def test_empty_earnings_skips_company_actions(self, runner):
        """Test Case: Empty API response should skip all mutation and notification actions."""
        runner.service.get_companies.return_value = {"AAPL": MagicMock(spec=Company)}
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
        latest_in_db = MagicMock(spec=Earnings)
        existing_quarter = MagicMock()
        existing_quarter.root = {"2026-01-01": latest_in_db}
        mock_company = MagicMock(spec=Company)
        mock_company.root = {"Q1": existing_quarter}
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        q1_earnings = MagicMock(spec=Earnings)
        q2_earnings = MagicMock(spec=Earnings)
        runner.client.get_earnings.return_value = {"Q1": q1_earnings, "Q2": q2_earnings}
        runner.almost_equals_earnings.return_value = False
        runner.run()
        runner.service.init_quarter.assert_called_once_with("NVDA", "Q2", q2_earnings)
        runner.service.new_earnings.assert_called_once_with("NVDA", "Q1", q1_earnings)
        runner.discord_post_earnings.assert_has_calls([
            call("NVDA", "Q2", None, q2_earnings),
            call("NVDA", "Q1", latest_in_db, q1_earnings),
        ])
        runner.log.info.assert_not_called()

    def test_init_new_company_posts_all_quarters_in_reverse_order(self, runner):
        """Test Case: New company should initialize once and post every quarter in reverse insertion order."""
        runner.service.get_companies.return_value = {"AAPL": None}
        q1_earnings = MagicMock(spec=Earnings)
        q2_earnings = MagicMock(spec=Earnings)
        q3_earnings = MagicMock(spec=Earnings)
        earnings_dict = {"Q1": q1_earnings, "Q2": q2_earnings, "Q3": q3_earnings}
        runner.client.get_earnings.return_value = earnings_dict
        runner.run()
        runner.service.init_company.assert_called_once_with("AAPL", earnings_dict)
        assert runner.discord_post_earnings.call_args_list == [
            call("AAPL", "Q3", None, q3_earnings),
            call("AAPL", "Q2", None, q2_earnings),
            call("AAPL", "Q1", None, q1_earnings),
        ]
