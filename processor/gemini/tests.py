from datetime import datetime
from unittest.mock import MagicMock, patch

import pytest

from gemini.models import ReportDate, Quarter
from stock_data_retriever import StockDataRetrieverRunner


class TestStockDataRetriever:
    @pytest.fixture
    def runner(self):
        """This defines the 'runner' argument used in your tests."""
        with patch('stock_data_retriever.GeminiClient') as mock_client, \
                patch('stock_data_retriever.FirebaseService') as mock_service, \
                patch('stock_data_retriever.DiscordClient') as mock_discord:
            instance = StockDataRetrieverRunner(gemini_api_key="fake-key", discord_webhook_key="fake-webhook-key")
            instance.log = MagicMock()
            return instance

    @patch("utils.is_past_date")
    @patch("gemini.stock_data_retriever.datetime")
    def test_init_new_company(self, mock_datetime, mock_is_past, runner):
        """Test Case: Company exists in list but data is None (needs init)."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)  # A Monday
        runner.service.get_companies.return_value = {"AAPL": None}
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "Q1-2026"
        mock_company.quarters = ["Q1"]
        runner.client.get_initial_stock_data.return_value = mock_company
        runner.run()
        runner.client.get_initial_stock_data.assert_called_with("AAPL")
        runner.service.init_company.assert_called_once()

    @patch("utils.is_past_date")
    @patch("stock_data_retriever.datetime")
    def test_sunday_revalidation_updates_date(self, mock_datetime, mock_is_past, runner):
        """Test Case: It's Sunday and a report date has changed."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)
        mock_is_past.return_value = False
        original_date = "2026-05-01"
        mock_quarter = MagicMock(id="Q1", report_date_this_quarter=original_date)
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "Q1"
        mock_company.quarters = {"Q1": mock_quarter}
        runner.service.get_companies.return_value = {"TSLA": mock_company}
        new_date = "2026-05-05"
        new_report = ReportDate(ticker="TSLA", quarter="Q1", report_date=new_date)
        mock_revalidated = MagicMock()
        mock_revalidated.report_dates = [new_report]
        runner.client.revalidate_report_dates.return_value = mock_revalidated
        runner.run()
        runner.service.update_report_date.assert_called_once_with(new_report)

    def test_missing_quarter_raises_exception(self, runner):
        """Test Case: Company exists but the current_quarter_id is invalid."""
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "MISSING"
        mock_company.quarters = {"EXISTING": MagicMock()}
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        runner.run()
        assert "[Err] quarter MISSING not found for NVDA" in runner.log.call_args[0][0]

    @patch("utils.is_past_date")
    @patch("stock_data_retriever.datetime")
    def test_reporting_success_flow(self, mock_datetime, mock_is_past, runner):
        """Test Case: Date passed, new earnings found -> Update DB and Discord."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        old_quarter = Quarter(id="25Q4", name="25Q4", report_date_this_quarter="2026-04-20", ending_month="26-03", report_date_previous_quarter="2026-01-20")
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "25Q4"
        mock_company.quarters = {"25Q4": old_quarter}
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        new_reported_quarter = Quarter(id="25Q4", name="25Q4", report_date_this_quarter="2026-04-20", ending_month="26-03", reported_eps="5.00", report_date_previous_quarter="2026-01-20")
        runner.client.get_quarter_report.return_value = new_reported_quarter
        next_quarter = Quarter(id="26Q1", name="26Q1", ending_month="26-06", report_date_previous_quarter="2026-04-20")
        runner.compose_new_quarter = MagicMock(return_value=next_quarter)
        runner.run()
        runner.service.report_quarter.assert_called_once_with("NVDA", new_reported_quarter)
        runner.service.create_quarter.assert_called_once_with("NVDA", next_quarter)
        runner.discord.post.assert_called_once()

    @patch("utils.is_past_date")
    def test_reporting_failed_idempotency(self, mock_is_past, runner):
        """Test Case: Date passed, but API returns same data -> Log error, don't update."""
        mock_is_past.return_value = True
        quarter_data = Quarter(id="25Q4", name="25Q4", report_date_this_quarter="2026-04-20", ending_month="26-03", report_date_previous_quarter="2026-01-20")
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "25Q4"
        mock_company.quarters = {"25Q4": quarter_data}
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        runner.client.get_quarter_report.return_value = quarter_data
        runner.run()
        runner.service.report_quarter.assert_not_called()
        runner.service.create_quarter.assert_not_called()
        assert "[Err] failed getting report for quarter 25Q4 of NVDA" in runner.log.call_args[0][0]

    @patch("stock_data_retriever.datetime")
    def test_revalidation_skipped_on_monday(self, mock_datetime, runner):
        """Test Case: Revalidation should NOT run if it is not Sunday."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "Q1"
        mock_company.quarters = {"Q1": MagicMock(report_date_this_quarter="2026-05-01")}
        runner.service.get_companies.return_value = {"TSLA": mock_company}
        runner.run()
        runner.client.revalidate_report_dates.assert_not_called()

    @patch("stock_data_retriever.datetime")
    def test_sunday_revalidation_no_change(self, mock_datetime, runner):
        """Test Case: Sunday revalidation runs, but dates match -> No DB write."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)  # Sunday
        date_str = "2026-05-01"
        mock_quarter = MagicMock(id="Q1", report_date_this_quarter=date_str)
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "Q1"
        mock_company.quarters = {"Q1": mock_quarter}
        runner.service.get_companies.return_value = {"TSLA": mock_company}
        same_report = ReportDate(ticker="TSLA", quarter="Q1", report_date=date_str)
        mock_revalidated = MagicMock(report_dates=[same_report])
        runner.client.revalidate_report_dates.return_value = mock_revalidated
        runner.run()
        runner.client.revalidate_report_dates.assert_called_once()
        runner.service.update_report_date.assert_not_called()

    def test_multi_company_mixed_state(self, runner):
        """Test Case: Loop handles one new company and one existing company."""
        runner.service.get_companies.return_value = {"AAPL": None, "MSFT": MagicMock()}
        mock_aapl = MagicMock()
        runner.client.get_initial_stock_data.return_value = mock_aapl
        runner.run()
        runner.client.get_initial_stock_data.assert_called_with("AAPL")
        runner.service.init_company.assert_called_with(id="AAPL", data=mock_aapl)
        assert runner.client.get_initial_stock_data.call_count == 1
