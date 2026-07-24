import logging
from datetime import date, datetime
from unittest.mock import create_autospec, patch

import pytest

from discord.client import DiscordChannel, DiscordClient
from error_reporting import ErrorReporter
from gemini.client import GeminiClient
from gemini.models import (
    Company,
    CompanyTarget,
    Info,
    Quarter,
    ReportDate,
    ReportDates,
    Target,
    Targets,
)
from gemini.service import FirebaseService
from gemini.retriever import StockDataRetrieverRunner


def make_quarter(
    quarter_id="25Q4",
    report_date="2026-05-01",
    name=None,
    ending_month="26-03",
    previous_report_date="2026-01-20",
    **overrides,
):
    data = {
        "id": quarter_id,
        "name": name or quarter_id,
        "ending_month": ending_month,
        "report_date_previous_quarter": previous_report_date,
        "report_date_this_quarter": report_date,
    }
    data.update(overrides)
    return Quarter(**data)


def make_company(ticker, current_quarter_id, quarters):
    return Company(
        info=Info(
            ticker=ticker,
            last_update="2026-04-01",
            current_quarter_id=current_quarter_id,
        ),
        quarters=quarters,
    )


class TestStockDataRetriever:
    @pytest.fixture
    def runner(self):
        instance = object.__new__(StockDataRetrieverRunner)
        instance.client = create_autospec(GeminiClient, instance=True)
        instance.service = create_autospec(FirebaseService, instance=True)
        instance.discord = create_autospec(
            DiscordClient,
            instance=True,
        )
        instance.log = create_autospec(logging.Logger, instance=True)
        instance.errors = create_autospec(ErrorReporter, instance=True)
        instance.client.get_price_targets.return_value = Targets(targets=[])
        yield instance

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_init_new_company(self, mock_datetime, mock_is_past, runner):
        """Test Case: Company exists in list but data is None (needs init)."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)  # A Monday
        runner.service.get_companies.return_value = {"AAPL": None}
        quarter = make_quarter(quarter_id="26Q1")
        mock_company = make_company("AAPL", "26Q1", {"26Q1": quarter})
        runner.client.get_initial_stock_data.return_value = mock_company
        runner.run()
        runner.client.get_initial_stock_data.assert_called_once_with("AAPL")
        runner.service.init_company.assert_called_once_with(id="AAPL", data=mock_company)

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_sunday_revalidation_updates_date(self, mock_datetime, mock_is_past, runner):
        """Test Case: It's Sunday and a report date has changed."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)
        mock_is_past.return_value = False
        original_date = "2026-05-01"
        mock_quarter = make_quarter(quarter_id="26Q1", report_date=original_date)
        mock_company = make_company("TSLA", "26Q1", {"26Q1": mock_quarter})
        runner.service.get_companies.return_value = {"TSLA": mock_company}
        new_date = "2026-05-05"
        new_report = ReportDate(ticker="TSLA", quarter="26Q1", report_date=new_date)
        runner.client.revalidate_report_dates.return_value = ReportDates(report_dates=[new_report])
        runner.run()
        runner.service.update_report_date.assert_called_once_with(new_report)

    def test_missing_quarter_raises_exception(self, runner):
        """Test Case: Company exists but the current_quarter_id is invalid."""
        existing_quarter = make_quarter(quarter_id="26Q1")
        mock_company = make_company("NVDA", "26Q2", {"26Q1": existing_quarter})
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        runner.run()
        runner.log.error.assert_called_once()
        assert "quarter 26Q2 not found for NVDA" in runner.log.error.call_args[0][0]

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_reporting_success_flow(self, mock_datetime, mock_is_past, runner):
        """Test Case: Date passed, new earnings found -> Update DB and Discord."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        old_quarter = make_quarter(report_date="2026-04-20")
        mock_company = make_company("NVDA", "25Q4", {"25Q4": old_quarter})
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        new_reported_quarter = make_quarter(report_date="2026-04-20", reported_eps="5.00")
        runner.client.get_quarter_report.return_value = new_reported_quarter
        next_quarter = make_quarter(
            quarter_id="26Q1",
            report_date="",
            ending_month="26-06",
            previous_report_date="2026-04-20",
        )
        runner.compose_new_quarter = create_autospec(runner.compose_new_quarter, return_value=next_quarter)
        runner.run()
        runner.service.report_quarter.assert_called_once_with("NVDA", new_reported_quarter)
        runner.service.create_quarter.assert_called_once_with("NVDA", next_quarter)
        runner.discord.post.assert_called_once()
        assert runner.discord.post.call_args.args[0] is DiscordChannel.EARNINGS

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_reporting_failed_idempotency(self, mock_datetime, mock_is_past, runner):
        """Test Case: Date passed, but API returns same data -> Log error, don't update."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        quarter_data = make_quarter(report_date="2026-04-20")
        mock_company = make_company("NVDA", "25Q4", {"25Q4": quarter_data})
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        runner.client.get_quarter_report.return_value = quarter_data
        runner.run()
        runner.service.report_quarter.assert_not_called()
        runner.service.create_quarter.assert_not_called()
        runner.log.error.assert_called_once()
        assert "failed getting report for quarter 25Q4 of NVDA" in runner.log.error.call_args[0][0]

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_revalidation_skipped_on_monday(self, mock_datetime, mock_is_past, runner):
        """Test Case: Revalidation should NOT run if it is not Sunday."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = False
        quarter = make_quarter(quarter_id="26Q1", report_date="2026-05-01")
        mock_company = make_company("TSLA", "26Q1", {"26Q1": quarter})
        runner.service.get_companies.return_value = {"TSLA": mock_company}
        runner.run()
        runner.client.revalidate_report_dates.assert_not_called()

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_sunday_revalidation_no_change(self, mock_datetime, mock_is_past, runner):
        """Test Case: Sunday revalidation runs, but dates match -> No DB write."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)  # Sunday
        mock_is_past.return_value = False
        date_str = "2026-05-01"
        mock_quarter = make_quarter(quarter_id="26Q1", report_date=date_str)
        mock_company = make_company("TSLA", "26Q1", {"26Q1": mock_quarter})
        runner.service.get_companies.return_value = {"TSLA": mock_company}
        same_report = ReportDate(ticker="TSLA", quarter="26Q1", report_date=date_str)
        runner.client.revalidate_report_dates.return_value = ReportDates(report_dates=[same_report])
        runner.run()
        runner.client.revalidate_report_dates.assert_called_once()
        runner.service.update_report_date.assert_not_called()

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_multi_company_mixed_state(self, mock_datetime, mock_is_past, runner):
        """Test Case: Loop handles one new company and one existing company."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = False
        existing_quarter = make_quarter(quarter_id="26Q1", report_date="2026-05-01")
        existing_company = make_company("MSFT", "26Q1", {"26Q1": existing_quarter})
        runner.service.get_companies.return_value = {"AAPL": None, "MSFT": existing_company}
        aapl_quarter = make_quarter(quarter_id="26Q1")
        mock_aapl = make_company("AAPL", "26Q1", {"26Q1": aapl_quarter})
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
        runner.errors.report.assert_called_once_with(
            error,
            logger=runner.log,
            source=runner.name,
            operation="run",
            context=None,
        )
        runner.client.get_initial_stock_data.assert_not_called()
        runner.client.get_quarter_report.assert_not_called()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_price_targets_use_previous_monday_through_sunday(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 20)
        company = make_company(
            "AAPL",
            "26Q2",
            {"26Q2": make_quarter(quarter_id="26Q2")},
        )
        runner.service.get_companies.return_value = {
            "AAPL": company,
        }
        target = Target(
            ticker="AAPL",
            institution="Important Research",
            date="2026-07-15",
            price="225.50",
            rating="Outperform",
            source="https://research.example.com/aapl",
        )
        runner.client.get_price_targets.return_value = Targets(
            targets=[target]
        )

        runner.run()

        runner.client.get_price_targets.assert_called_once_with(
            ["AAPL"],
            date(2026, 7, 13),
            date(2026, 7, 19),
        )
        runner.service.upsert_target.assert_called_once_with(
            "AAPL",
            CompanyTarget(
                institution="Important Research",
                date="2026-07-15",
                price="225.50",
                rating="Outperform",
                source="https://research.example.com/aapl",
            ),
        )
        runner.discord.post.assert_called_once()
        assert runner.discord.post.call_args.args[0] is DiscordChannel.EVENTLOG
        payload = runner.discord.post.call_args.args[1]
        embed = payload["embeds"][0]
        assert embed["title"] == "AAPL | Important Research"
        assert embed["fields"][0]["value"] == "$225.50"
        assert embed["fields"][1]["value"] == "Outperform"
        assert embed["fields"][2]["value"] == "2026-07-15"
        assert embed["fields"][3]["value"] == (
            "[Open source](https://research.example.com/aapl)"
        )
        runner.errors.report.assert_not_called()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_price_targets_are_not_requested_outside_monday(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        company = make_company(
            "AAPL",
            "26Q2",
            {"26Q2": make_quarter(quarter_id="26Q2")},
        )
        runner.service.get_companies.return_value = {"AAPL": company}

        runner.run()

        runner.client.get_price_targets.assert_not_called()
        runner.service.upsert_target.assert_not_called()
        runner.discord.post.assert_not_called()

    def test_company_processing_failure_does_not_stop_stock_run(self, runner):
        error = RuntimeError("Gemini unavailable")
        runner.service.get_companies.return_value = {"FAIL": None, "SKIPPED": None}
        recovered_company = make_company(
            "SKIPPED",
            "26Q1",
            {"26Q1": make_quarter(quarter_id="26Q1")},
        )
        runner.client.get_initial_stock_data.side_effect = [
            error,
            recovered_company,
        ]

        runner.run()

        assert runner.client.get_initial_stock_data.call_count == 2
        runner.service.init_company.assert_called_once_with(
            id="SKIPPED",
            data=recovered_company,
        )
        runner.errors.report.assert_called_once_with(
            error,
            logger=runner.log,
            source=runner.name,
            operation="process_company",
            context={"company_id": "FAIL"},
        )

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_shorter_revalidation_response_is_logged(self, mock_datetime, mock_is_past, runner):
        mock_datetime.now.return_value = datetime(2026, 4, 26)
        first = make_quarter(quarter_id="26Q1", report_date="2026-05-01")
        second = make_quarter(quarter_id="26Q2", report_date="2026-05-02")
        runner.service.get_companies.return_value = {
            "AAPL": make_company("AAPL", "26Q1", {"26Q1": first}),
            "MSFT": make_company("MSFT", "26Q2", {"26Q2": second}),
        }
        runner.client.revalidate_report_dates.return_value = ReportDates(report_dates=[
            ReportDate(ticker="AAPL", quarter="26Q1", report_date="2026-05-01")
        ])

        runner.run()

        error = runner.errors.report.call_args.args[0]
        assert isinstance(error, ValueError)
        assert "changed report-date identities" in str(error)
        runner.service.update_report_date.assert_not_called()
        runner.discord.post.assert_not_called()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_reordered_revalidation_response_is_rejected(self, mock_datetime, mock_is_past, runner):
        mock_datetime.now.return_value = datetime(2026, 4, 26)
        first = make_quarter(quarter_id="26Q1", report_date="2026-05-01")
        second = make_quarter(quarter_id="26Q2", report_date="2026-05-02")
        runner.service.get_companies.return_value = {
            "AAPL": make_company("AAPL", "26Q1", {"26Q1": first}),
            "MSFT": make_company("MSFT", "26Q2", {"26Q2": second}),
        }
        runner.client.revalidate_report_dates.return_value = ReportDates(report_dates=[
            ReportDate(ticker="MSFT", quarter="26Q2", report_date="2026-05-03"),
            ReportDate(ticker="AAPL", quarter="26Q1", report_date="2026-05-04"),
        ])

        runner.run()

        runner.service.update_report_date.assert_not_called()
        runner.errors.report.assert_called_once()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_changed_revalidation_identity_is_rejected(self, mock_datetime, mock_is_past, runner):
        mock_datetime.now.return_value = datetime(2026, 4, 26)
        quarter = make_quarter(quarter_id="26Q1", report_date="2026-05-01")
        runner.service.get_companies.return_value = {
            "AAPL": make_company("AAPL", "26Q1", {"26Q1": quarter}),
        }
        runner.client.revalidate_report_dates.return_value = ReportDates(report_dates=[
            ReportDate(ticker="MSFT", quarter="26Q1", report_date="2026-05-03"),
        ])

        runner.run()

        runner.service.update_report_date.assert_not_called()
        error = runner.errors.report.call_args.args[0]
        assert isinstance(error, ValueError)
        assert "('AAPL', '26Q1')" in str(error)
        assert "('MSFT', '26Q1')" in str(error)

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_reporting_failed_on_sunday_revalidates_report_date(self, mock_datetime, mock_is_past, runner):
        """Test Case: Failed report retrieval on Sunday should enqueue the report date for Sunday revalidation."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)  # Sunday
        mock_is_past.return_value = True
        quarter_data = make_quarter(report_date="2026-04-20")
        mock_company = make_company("NVDA", "25Q4", {"25Q4": quarter_data})
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
    @patch("gemini.retriever.datetime")
    def test_successful_report_on_sunday_still_revalidates_remaining_dates(self, mock_datetime, mock_is_past, runner):
        """Test Case: Successful reporting on Sunday should still revalidate queued future report dates at the end of the run."""
        mock_datetime.now.return_value = datetime(2026, 4, 26)  # Sunday
        mock_is_past.side_effect = [True, False]
        reported_quarter_data = make_quarter(report_date="2026-04-20")
        reported_company = make_company("NVDA", "25Q4", {"25Q4": reported_quarter_data})
        future_quarter = make_quarter(quarter_id="26Q1", report_date="2026-05-01")
        future_company = make_company("TSLA", "26Q1", {"26Q1": future_quarter})
        runner.service.get_companies.return_value = {
            "NVDA": reported_company,
            "TSLA": future_company
        }
        reported_quarter = make_quarter(report_date="2026-04-20", reported_eps="5.00")
        next_quarter = make_quarter(
            quarter_id="26Q1",
            report_date="",
            ending_month="26-06",
            previous_report_date="2026-04-20",
        )
        runner.client.get_quarter_report.return_value = reported_quarter
        runner.compose_new_quarter = create_autospec(runner.compose_new_quarter, return_value=next_quarter)
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
        previous_quarter = make_quarter(
            name="Q4 2025",
            ending_month="25-12",
            previous_report_date="2025-07-20",
            report_date="2025-10-20",
        )
        new_quarter = runner.compose_new_quarter(previous_quarter)
        assert new_quarter.id == "26Q1"
        assert new_quarter.name == "Q1 2026"
        assert new_quarter.ending_month == "26-03"
        assert new_quarter.report_date_previous_quarter == datetime(2025, 10, 20).date()

    @patch("gemini.retriever.datetime")
    def test_check_report_dates_next_week_logs_error_when_not_sunday(self, mock_datetime, runner):
        """Test Case: check_report_dates_next_week should log an error instead of posting when called on a non-Sunday."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)  # Monday
        runner.check_report_dates_next_week(ReportDates(report_dates=[]))
        runner.log.error.assert_called_once()
        assert "should run on Sunday, but is Monday" in runner.log.error.call_args[0][0]
        runner.discord.post.assert_not_called()

    @patch("gemini.retriever.datetime")
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
        assert runner.discord.post.call_args.args[0] is DiscordChannel.EARNINGS
        payload = runner.discord.post.call_args.args[1]
        embed = payload["embeds"][0]
        fields = embed["fields"]
        assert embed["title"] == "📅 Upcoming Earnings Reports"
        assert fields[0]["name"] == "**Monday** (2026-04-27)"
        assert fields[0]["value"] == "AAPL - 25Q4"
        assert fields[-1]["name"] == "**Friday** (2026-05-01)"
        assert fields[-1]["value"] == "MSFT - 25Q4"
