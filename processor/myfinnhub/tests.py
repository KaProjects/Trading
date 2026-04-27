from unittest.mock import MagicMock, patch

import pytest

from myfinnhub.earnings_retriever import FinnhubEarningsRetrieverRunner
from myfinnhub.models import Earnings, Company


class TestFinnhubEarningsRetriever:
    @pytest.fixture
    def runner(self):
        with patch('earnings_retriever.FirebaseService') as mock_service, \
                patch('earnings_retriever.FinnhubClient') as mock_client, \
                patch('earnings_retriever.DiscordClient') as mock_discord:
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
        assert "no change" in runner.log.call_args[0][0].lower()

    def test_exception_per_company_does_not_stop_loop(self, runner):
        """Test Case: One company fails, but the loop continues for the next."""
        runner.service.get_companies.return_value = {"FAIL": MagicMock(), "SUCCESS": MagicMock()}
        runner.client.get_earnings.side_effect = [Exception("API Error"), {}]
        runner.run()
        actual_log = runner.log.call_args_list[0][0][0].lower()
        assert "error while processing fail" in actual_log
        assert runner.client.get_earnings.call_count == 2
