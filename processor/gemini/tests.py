from datetime import datetime
from unittest.mock import MagicMock, patch

import pytest

from gemini.models import ReportDate, Quarter, ReportDates
from gemini.stock_data_retriever import StockDataRetrieverRunner


class TestStockDataRetriever:
    @pytest.fixture
    def runner(self):
        """This defines the 'runner' argument used in your tests."""
        with patch('gemini.stock_data_retriever.GeminiClient') as mock_client, \
                patch('gemini.stock_data_retriever.FirebaseService') as mock_service, \
                patch('gemini.stock_data_retriever.DiscordClient') as mock_discord:
            instance = StockDataRetrieverRunner(gemini_api_key="fake-key", discord_webhook_key="fake-webhook-key")
            instance.client = mock_client.return_value
            instance.service = mock_service.return_value
            instance.discord = mock_discord.return_value
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
    @patch("gemini.stock_data_retriever.datetime")
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
        runner.log.error.assert_called_once()
        assert "quarter MISSING not found for NVDA" in runner.log.error.call_args[0][0]

    @patch("utils.is_past_date")
    @patch("gemini.stock_data_retriever.datetime")
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
    @patch("gemini.stock_data_retriever.datetime")
    def test_reporting_failed_idempotency(self, mock_datetime, mock_is_past, runner):
        """Test Case: Date passed, but API returns same data -> Log error, don't update."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
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
        runner.log.error.assert_called_once()
        assert "failed getting report for quarter 25Q4 of NVDA" in runner.log.error.call_args[0][0]

    @patch("utils.is_past_date")
    @patch("gemini.stock_data_retriever.datetime")
    def test_revalidation_skipped_on_monday(self, mock_datetime, mock_is_past, runner):
        """Test Case: Revalidation should NOT run if it is not Sunday."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = False
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "Q1"
        mock_company.quarters = {"Q1": MagicMock(id="Q1", report_date_this_quarter="2026-05-01")}
        runner.service.get_companies.return_value = {"TSLA": mock_company}
        runner.run()
        runner.client.revalidate_report_dates.assert_not_called()

    @patch("utils.is_past_date")
    @patch("gemini.stock_data_retriever.datetime")
    def test_sunday_revalidation_no_change(self, mock_datetime, mock_is_past, runner):
        """Test Case: Sunday revalidation runs, but dates match -> No DB write."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)  # Sunday
        mock_is_past.return_value = False
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

    @patch("utils.is_past_date")
    @patch("gemini.stock_data_retriever.datetime")
    def test_multi_company_mixed_state(self, mock_datetime, mock_is_past, runner):
        """Test Case: Loop handles one new company and one existing company."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = False
        existing_quarter = MagicMock(id="Q1", report_date_this_quarter="2026-05-01")
        existing_company = MagicMock()
        existing_company.info.current_quarter_id = "Q1"
        existing_company.quarters = {"Q1": existing_quarter}
        runner.service.get_companies.return_value = {"AAPL": None, "MSFT": existing_company}
        mock_aapl = MagicMock()
        runner.client.get_initial_stock_data.return_value = mock_aapl
        runner.run()
        runner.client.get_initial_stock_data.assert_called_once_with("AAPL")
        runner.service.init_company.assert_called_once_with(id="AAPL", data=mock_aapl)
        runner.client.revalidate_report_dates.assert_not_called()

    def test_run_logs_exception_when_loading_companies_fails(self, runner):
        """Test Case: Top-level company loading failure should be logged and stop the run."""
        error = Exception("DB Error")
        runner.service.get_companies.side_effect = error
        runner.run()
        runner.log.exception.assert_called_once_with(error)
        runner.client.get_initial_stock_data.assert_not_called()
        runner.client.get_quarter_report.assert_not_called()

    @patch("utils.is_past_date")
    @patch("gemini.stock_data_retriever.datetime")
    def test_reporting_failed_on_sunday_revalidates_report_date(self, mock_datetime, mock_is_past, runner):
        """Test Case: Failed report retrieval on Sunday should enqueue the report date for Sunday revalidation."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)  # Sunday
        mock_is_past.return_value = True
        quarter_data = Quarter(
            id="25Q4",
            name="25Q4",
            report_date_this_quarter="2026-04-20",
            ending_month="26-03",
            report_date_previous_quarter="2026-01-20"
        )
        mock_company = MagicMock()
        mock_company.info.current_quarter_id = "25Q4"
        mock_company.quarters = {"25Q4": quarter_data}
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        runner.client.get_quarter_report.return_value = quarter_data
        updated_report = ReportDate(ticker="NVDA", quarter="25Q4", report_date="2026-04-28")
        runner.client.revalidate_report_dates.return_value = ReportDates(report_dates=[updated_report])
        runner.run()
        runner.service.report_quarter.assert_not_called()
        runner.service.create_quarter.assert_not_called()
        runner.log.error.assert_called_once()
        assert "failed getting report for quarter 25Q4 of NVDA" in runner.log.error.call_args[0][0]
        runner.client.revalidate_report_dates.assert_called_once()
        runner.service.update_report_date.assert_called_once_with(updated_report)

    @patch("utils.is_past_date")
    @patch("gemini.stock_data_retriever.datetime")
    def test_successful_report_on_sunday_still_revalidates_remaining_dates(self, mock_datetime, mock_is_past, runner):
        """Test Case: Successful reporting on Sunday should still revalidate queued future report dates at the end of the run."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)  # Sunday
        mock_is_past.side_effect = [True, False]
        reported_company = MagicMock()
        reported_company.info.current_quarter_id = "25Q4"
        reported_company.quarters = {
            "25Q4": Quarter(
                id="25Q4",
                name="25Q4",
                report_date_this_quarter="2026-04-20",
                ending_month="26-03",
                report_date_previous_quarter="2026-01-20"
            )
        }
        future_quarter = MagicMock(id="26Q1", report_date_this_quarter="2026-05-01")
        future_company = MagicMock()
        future_company.info.current_quarter_id = "26Q1"
        future_company.quarters = {"26Q1": future_quarter}
        runner.service.get_companies.return_value = {
            "NVDA": reported_company,
            "TSLA": future_company
        }
        reported_quarter = Quarter(
            id="25Q4",
            name="25Q4",
            report_date_this_quarter="2026-04-20",
            ending_month="26-03",
            report_date_previous_quarter="2026-01-20",
            reported_eps="5.00"
        )
        next_quarter = Quarter(
            id="26Q1",
            name="26Q1",
            ending_month="26-06",
            report_date_previous_quarter="2026-04-20"
        )
        runner.client.get_quarter_report.return_value = reported_quarter
        runner.compose_new_quarter = MagicMock(return_value=next_quarter)
        revalidated_report = ReportDate(ticker="TSLA", quarter="26Q1", report_date="2026-05-02")
        runner.client.revalidate_report_dates.return_value = ReportDates(report_dates=[revalidated_report])
        runner.run()
        runner.service.report_quarter.assert_called_once_with("NVDA", reported_quarter)
        runner.service.create_quarter.assert_called_once_with("NVDA", next_quarter)
        runner.client.revalidate_report_dates.assert_called_once()
        queued_dates = runner.client.revalidate_report_dates.call_args[0][0]
        assert queued_dates.report_dates[0].ticker == "TSLA"
        assert queued_dates.report_dates[0].quarter == "26Q1"
        runner.service.update_report_date.assert_called_once_with(revalidated_report)

    def test_compose_new_quarter_rolls_q4_into_next_year(self, runner):
        """Test Case: compose_new_quarter should roll Q4 into Q1 of the next year with the correct ending month."""
        previous_quarter = Quarter(
            id="25Q4",
            name="Q4 2025",
            ending_month="25-12",
            report_date_previous_quarter="2025-07-20",
            report_date_this_quarter="2025-10-20"
        )
        new_quarter = runner.compose_new_quarter(previous_quarter)
        assert new_quarter.id == "26Q1"
        assert new_quarter.name == "Q1 2026"
        assert new_quarter.ending_month == "26-03"
        assert new_quarter.report_date_previous_quarter == "2025-10-20"

    @patch("gemini.stock_data_retriever.datetime")
    def test_check_report_dates_next_week_logs_error_when_not_sunday(self, mock_datetime, runner):
        """Test Case: check_report_dates_next_week should log an error instead of posting when called on a non-Sunday."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)  # Monday
        runner.check_report_dates_next_week(ReportDates(report_dates=[]))
        runner.log.error.assert_called_once()
        assert "should run on Sunday, but is Monday" in runner.log.error.call_args[0][0]
        runner.discord.post.assert_not_called()

    @patch("gemini.stock_data_retriever.datetime")
    def test_check_report_dates_next_week_posts_grouped_schedule_on_sunday(self, mock_datetime, runner):
        """Test Case: check_report_dates_next_week should group next-week reports by weekday and post one Discord payload on Sunday."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)  # Sunday
        mock_datetime.strptime.side_effect = datetime.strptime
        report_dates = ReportDates(report_dates=[
            ReportDate(ticker="AAPL", quarter="25Q4", report_date="2026-04-27"),
            ReportDate(ticker="MSFT", quarter="25Q4", report_date="2026-05-01"),
        ])
        runner.check_report_dates_next_week(report_dates)
        runner.discord.post.assert_called_once()
        payload = runner.discord.post.call_args[0][0]
        embed = payload["embeds"][0]
        fields = embed["fields"]
        assert embed["title"] == "📅 Upcoming Earnings Reports"
        assert fields[0]["name"] == "**Monday** (2026-04-27)"
        assert fields[0]["value"] == "AAPL - 25Q4"
        assert fields[-1]["name"] == "**Friday** (2026-05-01)"
        assert fields[-1]["value"] == "MSFT - 25Q4"
