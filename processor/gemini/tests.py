from datetime import datetime
from unittest.mock import MagicMock, patch

import pytest

from stock_data_retriever import StockDataRetrieverRunner


class TestStockDataRetriever:
    @pytest.fixture
    def runner(self):
        """This defines the 'runner' argument used in your tests."""
        with patch('stock_data_retriever.GeminiClient') as mock_client, \
                patch('stock_data_retriever.FirebaseService') as mock_service:
            instance = StockDataRetrieverRunner(gemini_api_key="fake-key")
            instance.log = MagicMock()
            return instance

    @patch("utils.is_past_date")
    @patch("gemini.stock_data_retriever.datetime")
    def test_init_new_company(self, mock_datetime, mock_is_past, runner):
        """Test case: Company exists in list but data is None (needs init)."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)  # A Monday
        runner.service.get_companies.return_value = {"AAPL": None}
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "Q1-2026"
        mock_company.quarters = ["Q1"]
        runner.client.get_initial_stock_data.return_value = mock_company
        runner.run()
        runner.client.get_initial_stock_data.assert_called_with("AAPL")
        runner.service.init_company.assert_called_once()
        assert "company AAPL initiated with 1 quarters with Q1-2026 being the current." in runner.log.call_args[0][0]

    @patch("utils.is_past_date")
    @patch("gemini.stock_data_retriever.datetime")
    def test_sunday_revalidation_updates_date(self, mock_datetime, mock_is_past, runner):
        """Test case: It's Sunday and a report date has changed."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)
        mock_is_past.return_value = False  # Not in the past
        mock_quarter = MagicMock(id="Q1", report_date_this_quarter="2026-05-01")
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "Q1"
        mock_company.quarters = {"Q1": mock_quarter}
        runner.service.get_companies.return_value = {"TSLA": mock_company}
        new_report = MagicMock(ticker="TSLA", report_date="2026-05-05")
        mock_revalidated = MagicMock()
        mock_revalidated.report_dates = [new_report]
        runner.client.revalidate_report_dates.return_value = mock_revalidated
        runner.run()
        runner.service.update_report_date.assert_called_once_with(new_report)
        runner.client.revalidate_report_dates.assert_called_once()

    def test_missing_quarter_raises_exception(self, runner):
        """Test case: Company exists but the current_quarter_id is invalid."""
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "MISSING"
        mock_company.quarters = {"EXISTING": MagicMock()}
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        runner.run()
        assert runner.log.call_count >= 2
        all_log_messages = [call.args[0] for call in runner.log.call_args_list]
        assert any("Quarter MISSING not found for NVDA" in msg for msg in all_log_messages)
        assert "^^^ exception occurred!" in all_log_messages
