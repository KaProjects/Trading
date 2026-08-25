import logging
from datetime import date, datetime, timedelta
from unittest.mock import create_autospec, patch

import pytest

from discord.client import DiscordClient
from error_reporting import ErrorReporter
from gemini import discord_templates
from gemini.client import (
    GeminiClient,
    InitialCompanyResult,
    InvalidInitialCompanyResponse,
    QuarterReportResult,
)
from gemini.models import (
    Company,
    CompanyTarget,
    Info,
    InstitutionRecord,
    Quarter,
    ReportDate,
    ReportDates,
    Target,
    TargetReport,
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


def make_complete_quarter(**overrides):
    data = {
        "reported_eps": "1.25",
        "reported_revenues": "1000",
        "reported_gross_profit": "500",
        "reported_operating_income": "300",
        "reported_net_income": "200",
        "reported_capex": "75",
        "reported_free_cash_flow": "180",
        "reported_div": "0",
        "reported_shares": "100",
        "price_min": "90",
        "price_max": "110",
    }
    data.update(overrides)
    return make_quarter(**data)


def make_company(
    ticker,
    current_quarter_id,
    quarters,
    targets=None,
    currency="$",
):
    return Company(
        info=Info(
            ticker=ticker,
            currency=currency,
            last_update="2026-04-01",
            current_quarter_id=current_quarter_id,
        ),
        quarters=quarters,
        targets=targets or {},
    )


def make_initial_result(company, errors=(), raw_response=""):
    return InitialCompanyResult(
        company=company,
        errors=tuple(errors),
        raw_response=raw_response,
    )


def make_quarter_report_result(quarter, raw_response=""):
    return QuarterReportResult(
        quarter=quarter,
        raw_response=raw_response,
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
        instance.service.get_institutions.return_value = {}
        instance.discord.post_if_channel_exists.return_value = None
        yield instance

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_init_new_company(self, mock_datetime, mock_is_past, runner):
        """Test Case: Company exists in list but data is None (needs init)."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)  # A Monday
        runner.service.get_companies.return_value = {"AAPL": None}
        quarter = make_quarter(quarter_id="26Q1")
        mock_company = make_company("AAPL", "26Q1", {"26Q1": quarter})
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(mock_company)
        )
        runner.run()
        runner.client.get_initial_stock_data.assert_called_once_with("AAPL")
        runner.service.init_company.assert_called_once_with(id="AAPL", data=mock_company)

    def test_initialization_errors_are_reported_and_company_is_not_persisted(
        self,
        runner,
    ):
        quarter = make_quarter(quarter_id="26Q3")
        company = make_company("ASML", "26Q3", {"26Q3": quarter})
        response = make_initial_result(
            company,
            errors=[
                "26Q2 reported dividend was unavailable.",
                "26Q1 price range was based on limited public data.",
            ],
            raw_response='{"errors":["unavailable data"]}',
        )
        runner.service.get_companies.return_value = {"ASML": None}
        runner.client.get_initial_stock_data.return_value = response

        runner.run()

        runner.service.init_company.assert_not_called()
        runner.errors.report_error_message.assert_called_once_with(
            "Gemini initialization failed:\n"
            "1. 26Q2 reported dividend was unavailable.\n"
            "2. 26Q1 price range was based on limited public data.\n\n"
            "================ GEMINI RESPONSE START ================\n"
            '{"errors":["unavailable data"]}\n'
            "================= GEMINI RESPONSE END =================",
            logger=runner.log,
            source=runner.name,
            operation="initialize_company",
            context={
                "company_id": "ASML",
                "currency": "$",
                "error_count": 2,
            },
        )

    def test_non_usd_optional_fields_are_warned_and_persisted(
        self,
        runner,
    ):
        current_quarter = make_quarter(quarter_id="26Q3")
        reported_quarter = make_quarter(
            quarter_id="26Q2",
            reported_revenues="1000",
            reported_net_income="200",
        )
        company = make_company(
            "ASML",
            "26Q3",
            {
                "26Q3": current_quarter,
                "26Q2": reported_quarter,
            },
            currency="€",
        )
        raw_response = '{"errors":["optional data unavailable"]}'
        runner.service.get_companies.return_value = {"ASML": None}
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(
                company,
                errors=["26Q2 optional data unavailable."],
                raw_response=raw_response,
            )
        )

        runner.run()

        runner.service.init_company.assert_called_once_with(
            id="ASML",
            data=company,
        )
        runner.errors.report_error_message.assert_not_called()
        runner.errors.report_warning_message.assert_called_once()
        warning_call = runner.errors.report_warning_message.call_args
        assert warning_call.kwargs["context"] == {
            "company_id": "ASML",
            "currency": "€",
            "error_count": 2,
        }
        assert "26Q2 optional data unavailable" in warning_call.args[0]
        assert "26Q2.reported_div" in warning_call.args[0]
        assert raw_response in warning_call.args[0]

    def test_non_usd_missing_required_fields_is_not_persisted(
        self,
        runner,
    ):
        current_quarter = make_quarter(quarter_id="26Q3")
        reported_quarter = make_quarter(
            quarter_id="26Q2",
            reported_net_income="200",
        )
        company = make_company(
            "ASML",
            "26Q3",
            {
                "26Q3": current_quarter,
                "26Q2": reported_quarter,
            },
            currency="€",
        )
        runner.service.get_companies.return_value = {"ASML": None}
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(
                company,
                errors=["26Q2 revenue unavailable."],
                raw_response='{"errors":["revenue unavailable"]}',
            )
        )

        runner.run()

        runner.service.init_company.assert_not_called()
        runner.errors.report_warning_message.assert_not_called()
        runner.errors.report_error_message.assert_called_once()
        error_call = runner.errors.report_error_message.call_args
        assert "Required reported-quarter fields are missing" in (
            error_call.args[0]
        )
        assert "26Q2.reported_revenues" in error_call.args[0]
        assert error_call.kwargs["context"] == {
            "company_id": "ASML",
            "currency": "€",
            "error_count": 2,
        }

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

    def test_missing_current_quarter_reinitializes_company(self, runner):
        existing_quarter = make_quarter(
            quarter_id="26Q1",
            reported_eps="1.25",
            reported_revenues="1000",
            reported_gross_profit="500",
            reported_operating_income="300",
            reported_net_income="200",
            reported_capex="75",
            reported_free_cash_flow="180",
            reported_div="0",
            reported_shares="100",
            price_min="90",
            price_max="110",
        )
        incomplete_company = make_company(
            "NVDA",
            "26Q2",
            {"26Q1": existing_quarter},
        )
        current_quarter = make_quarter(quarter_id="26Q2")
        initialized_company = make_company(
            "NVDA",
            "26Q2",
            {
                "26Q1": existing_quarter,
                "26Q2": current_quarter,
            },
        )
        runner.service.get_companies.return_value = {
            "NVDA": incomplete_company,
        }
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(initialized_company)
        )

        runner.run()

        runner.client.get_initial_stock_data.assert_called_once_with("NVDA")
        runner.service.init_company.assert_called_once_with(
            id="NVDA",
            data=initialized_company,
        )

    def test_invalid_initial_company_response_is_not_retried(
        self,
        runner,
    ):
        error = InvalidInitialCompanyResponse(
            ticker="ASML",
            violations=["quarter response is invalid"],
            raw_response='{"quarters": []}',
        )
        runner.service.get_companies.return_value = {"ASML": None}
        runner.client.get_initial_stock_data.side_effect = error

        runner.run()

        runner.client.get_initial_stock_data.assert_called_once_with("ASML")
        runner.service.init_company.assert_not_called()
        runner.errors.report.assert_called_once_with(
            error,
            logger=runner.log,
            source=runner.name,
            operation="process_company",
            context={"company_id": "ASML"},
        )

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_reporting_success_flow(self, mock_datetime, mock_is_past, runner):
        """Test Case: Date passed, new earnings found -> Update DB and Discord."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        old_quarter = make_quarter(report_date="2026-04-20")
        mock_company = make_company("NVDA", "25Q4", {"25Q4": old_quarter})
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        new_reported_quarter = make_complete_quarter(
            report_date="2026-04-20",
            reported_eps="5.00",
        )
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(new_reported_quarter)
        )
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
        runner.discord.post_earnings.assert_called_once()
        payload = runner.discord.post_earnings.call_args.args[0]
        expected_report = discord_templates.quarter_report(
            new_reported_quarter,
            "NVDA",
        )
        runner.discord.post_if_channel_exists.assert_called_once_with(
            "NVDA",
            discord_templates.ticker_quarter_report(
                new_reported_quarter
            ),
        )
        assert payload == expected_report
        assert payload["embeds"][0]["title"].startswith("NVDA - ")

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_report_posts_link_to_earnings_when_ticker_channel_exists(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        old_quarter = make_quarter(report_date="2026-04-20")
        company = make_company("NVDA", "25Q4", {"25Q4": old_quarter})
        runner.service.get_companies.return_value = {"NVDA": company}
        reported_quarter = make_complete_quarter(
            report_date="2026-04-20",
            reported_eps="5.00",
        )
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(reported_quarter)
        )
        runner.compose_new_quarter = create_autospec(
            runner.compose_new_quarter,
            return_value=make_quarter(
                quarter_id="26Q1",
                report_date="",
                ending_month="26-06",
                previous_report_date="2026-04-20",
            ),
        )
        message_url = (
            "https://discord.com/channels/guild-id/nvda-id/message-id"
        )
        runner.discord.post_if_channel_exists.return_value = message_url

        runner.run()

        report = discord_templates.quarter_report(
            reported_quarter,
            "NVDA",
        )
        runner.discord.post_if_channel_exists.assert_called_once_with(
            "NVDA",
            discord_templates.ticker_quarter_report(reported_quarter),
        )
        runner.discord.post_earnings.assert_called_once_with({
            "username": "Quarterly Results Reporter",
            "avatar_url": (
                "https://cdn-icons-png.flaticon.com/512/1390/1390704.png"
            ),
            "content": (
                "**NVDA reported earnings.** "
                f"[View the report in #NVDA]({message_url})"
            ),
        })

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_reporting_failed_idempotency(self, mock_datetime, mock_is_past, runner):
        """Test Case: Date passed, but API returns same data -> Log error, don't update."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        quarter_data = make_quarter(report_date="2026-04-20")
        mock_company = make_company("NVDA", "25Q4", {"25Q4": quarter_data})
        runner.service.get_companies.return_value = {"NVDA": mock_company}
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(quarter_data)
        )
        runner.run()
        runner.service.report_quarter.assert_not_called()
        runner.service.create_quarter.assert_not_called()
        runner.log.error.assert_called_once()
        assert "failed getting report for quarter 25Q4 of NVDA" in runner.log.error.call_args[0][0]

    @patch("utils.is_past_date", return_value=True)
    @patch("gemini.retriever.datetime")
    def test_non_usd_partial_quarter_report_is_warned_and_persisted(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        current_quarter = make_quarter(report_date="2026-04-20")
        company = make_company(
            "ASML",
            "25Q4",
            {"25Q4": current_quarter},
            currency="€",
        )
        reported_quarter = make_quarter(
            report_date="2026-04-20",
            reported_revenues="1000",
            reported_net_income="200",
        )
        raw_response = reported_quarter.model_dump_json()
        runner.service.get_companies.return_value = {"ASML": company}
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(reported_quarter, raw_response)
        )
        next_quarter = make_quarter(
            quarter_id="26Q1",
            report_date="",
            ending_month="26-06",
            previous_report_date="2026-04-20",
        )
        runner.compose_new_quarter = create_autospec(
            runner.compose_new_quarter,
            return_value=next_quarter,
        )

        runner.run()

        runner.service.report_quarter.assert_called_once_with(
            "ASML",
            reported_quarter,
        )
        runner.service.create_quarter.assert_called_once_with(
            "ASML",
            next_quarter,
        )
        runner.errors.report_error_message.assert_not_called()
        runner.errors.report_warning_message.assert_called_once()
        warning_call = runner.errors.report_warning_message.call_args
        assert warning_call.kwargs["context"] == {
            "company_id": "ASML",
            "currency": "€",
            "quarter_id": "25Q4",
            "missing_field_count": 9,
        }
        assert "reported_gross_profit" in warning_call.args[0]
        assert raw_response in warning_call.args[0]

    @patch("utils.is_past_date", return_value=True)
    @patch("gemini.retriever.datetime")
    def test_non_usd_quarter_report_missing_revenue_is_rejected(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        current_quarter = make_quarter(report_date="2026-04-20")
        company = make_company(
            "ASML",
            "25Q4",
            {"25Q4": current_quarter},
            currency="€",
        )
        reported_quarter = make_quarter(
            report_date="2026-04-20",
            reported_net_income="200",
        )
        runner.service.get_companies.return_value = {"ASML": company}
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(
                reported_quarter,
                reported_quarter.model_dump_json(),
            )
        )

        runner.run()

        runner.service.report_quarter.assert_not_called()
        runner.service.create_quarter.assert_not_called()
        runner.errors.report_warning_message.assert_not_called()
        runner.errors.report_error_message.assert_called_once()
        error_call = runner.errors.report_error_message.call_args
        assert "Required quarter report fields are missing" in (
            error_call.args[0]
        )
        assert "reported_revenues" in error_call.args[0]
        assert error_call.kwargs["context"]["currency"] == "€"

    @patch("utils.is_past_date", return_value=True)
    @patch("gemini.retriever.datetime")
    def test_usd_partial_quarter_report_is_rejected(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        current_quarter = make_quarter(report_date="2026-04-20")
        company = make_company(
            "NVDA",
            "25Q4",
            {"25Q4": current_quarter},
        )
        reported_quarter = make_quarter(
            report_date="2026-04-20",
            reported_revenues="1000",
            reported_net_income="200",
        )
        runner.service.get_companies.return_value = {"NVDA": company}
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(
                reported_quarter,
                reported_quarter.model_dump_json(),
            )
        )

        runner.run()

        runner.service.report_quarter.assert_not_called()
        runner.service.create_quarter.assert_not_called()
        runner.errors.report_warning_message.assert_not_called()
        runner.errors.report_error_message.assert_called_once()
        assert "USD quarter report is incomplete" in (
            runner.errors.report_error_message.call_args.args[0]
        )

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
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(mock_aapl)
        )
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
    def test_price_targets_use_today_and_two_previous_days(
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
        runner.service.get_companies.return_value = {
            "AAPL": company,
        }
        target = Target(
            ticker="AAPL",
            institution="Important Research",
            date="2026-07-20",
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
            date(2026, 7, 19),
            date(2026, 7, 21),
        )
        runner.service.create_institutions.assert_called_once_with({
            "important-research": InstitutionRecord(
                name="Important Research",
                aliases={
                    "important-research": "Important Research",
                },
                enabled=True,
            ),
        })
        runner.service.upsert_target.assert_called_once_with(
            "AAPL",
            CompanyTarget(
                institution="Important Research",
                date="2026-07-20",
                price="225.50",
                rating="Outperform",
                source="https://research.example.com/aapl",
            ),
        )
        runner.discord.post_eventlog.assert_called_once()
        payload = runner.discord.post_eventlog.call_args.args[0]
        assert payload["username"] == "Institutional Price Target Reporter"
        assert payload["avatar_url"].endswith("/1872/1872505.png")
        embed = payload["embeds"][0]
        assert embed["title"] == "🎯 AAPL | $225.50 | 2026-07-20"
        assert embed["fields"][0] == {
            "name": "Important Research",
            "value": "Outperform",
            "inline": False,
        }
        assert embed["fields"][1] == {
            "name": "Source",
            "value": "https://research.example.com/aapl",
            "inline": False,
        }
        assert len(embed["fields"]) == 2
        runner.client.get_target_report.assert_not_called()
        runner.errors.report.assert_not_called()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_price_target_alias_matches_unchanged_existing_target(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        existing_target = CompanyTarget(
            institution="Bank Of America Securities",
            date="2026-07-19",
            price="225",
            rating="Buy",
            source="existing.example.com",
        )
        runner.service.get_companies.return_value = {
            "AAPL": make_company(
                "AAPL",
                "26Q2",
                {"26Q2": make_quarter(quarter_id="26Q2")},
                targets={"existing": existing_target},
            ),
        }
        runner.service.get_institutions.return_value = {
            "bank-of-america": InstitutionRecord(
                name="Bank of America",
                aliases={
                    "bank-of-america": "Bank of America",
                    "bank-of-america-securities": (
                        "Bank Of America Securities"
                    ),
                    "bofa-securities": "BofA Securities",
                },
                enabled=True,
            ),
        }
        runner.client.get_price_targets.return_value = Targets(targets=[
            Target(
                ticker="AAPL",
                institution="BofA Securities",
                date="2026-07-21",
                price="225",
                rating="Buy",
                source="new.example.com",
            ),
        ])

        runner.run()

        runner.service.upsert_target.assert_not_called()
        runner.discord.post_eventlog.assert_not_called()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_price_target_is_persisted_with_canonical_institution_name(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        runner.service.get_companies.return_value = {
            "AAPL": make_company(
                "AAPL",
                "26Q2",
                {"26Q2": make_quarter(quarter_id="26Q2")},
            ),
        }
        runner.service.get_institutions.return_value = {
            "baird": InstitutionRecord(
                name="Baird",
                aliases={
                    "baird": "Baird",
                    "robert-w-baird": "Robert W. Baird",
                },
                enabled=True,
            ),
        }
        runner.client.get_price_targets.return_value = Targets(targets=[
            Target(
                ticker="AAPL",
                institution="Robert W. Baird",
                date="2026-07-21",
                price="230",
                rating="Outperform",
                source="new.example.com",
            ),
        ])

        runner.run()

        runner.service.upsert_target.assert_called_once_with(
            "AAPL",
            CompanyTarget(
                institution="Baird",
                date="2026-07-21",
                price="230",
                rating="Outperform",
                source="new.example.com",
            ),
        )
        payload = runner.discord.post_eventlog.call_args.args[0]
        assert payload["embeds"][0]["fields"][0]["name"] == "Baird"

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_disabled_institution_is_not_persisted_or_notified(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        runner.service.get_companies.return_value = {
            "AAPL": make_company(
                "AAPL",
                "26Q2",
                {"26Q2": make_quarter(quarter_id="26Q2")},
            ),
        }
        runner.service.get_institutions.return_value = {
            "rosenblatt": InstitutionRecord(
                name="Rosenblatt",
                aliases={
                    "rosenblatt": "Rosenblatt",
                    "rosenblatt-securities": "Rosenblatt Securities",
                },
                enabled=False,
            ),
        }
        runner.client.get_price_targets.return_value = Targets(targets=[
            Target(
                ticker="AAPL",
                institution="Rosenblatt Securities",
                date="2026-07-21",
                price="230",
                rating="Buy",
                source="new.example.com",
            ),
        ])

        runner.run()

        runner.service.upsert_target.assert_not_called()
        runner.discord.post_if_channel_exists.assert_not_called()
        runner.discord.post_eventlog.assert_not_called()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_trusted_institution_target_is_enriched_before_persistence(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        runner.service.get_companies.return_value = {
            "AMD": make_company(
                "AMD",
                "26Q2",
                {"26Q2": make_quarter(quarter_id="26Q2")},
            ),
        }
        runner.service.get_institutions.return_value = {
            "baird": InstitutionRecord(
                name="Baird",
                aliases={"baird": "Baird"},
                enabled=True,
                trusted=True,
            ),
        }
        target = Target(
            ticker="AMD",
            institution="Baird",
            date="2026-07-21",
            price="250",
            rating="Outperform",
            source="https://research.example.com/amd",
        )
        report = TargetReport(
            overview="Baird expects stronger data-center demand.",
            key_takeaways=[
                "The price target was increased to $250.",
                "Baird maintained its Outperform rating.",
            ],
        )
        enriched_target = target.model_copy(update={"report": report})
        runner.client.get_price_targets.return_value = Targets(
            targets=[target]
        )
        runner.client.get_target_report.return_value = enriched_target

        runner.run()

        runner.client.get_target_report.assert_called_once_with(target)
        runner.service.upsert_target.assert_called_once_with(
            "AMD",
            CompanyTarget(
                institution="Baird",
                date="2026-07-21",
                price="250",
                rating="Outperform",
                source="https://research.example.com/amd",
                report=report,
            ),
        )
        embed = runner.discord.post_eventlog.call_args.args[0]["embeds"][0]
        assert len(embed["fields"]) == 2
        assert embed["description"] == (
            "**Overview**\n\n"
            "Baird expects stronger data-center demand.\n\n"
            "**Key takeaways**\n"
            "• The price target was increased to $250.\n"
            "• Baird maintained its Outperform rating.\n\n"
            "\u200b"
        )

    def test_price_target_persists_normalized_report_that_fits_discord(
        self,
        runner,
    ):
        report = TargetReport(
            overview="x" * 1005,
            key_takeaways=[
                "t" * 505,
                "u" * 505,
                "v" * 505,
                "w" * 505,
                "Fifth",
            ],
        )
        target = Target(
            ticker="AMD",
            institution="Baird",
            date="2026-07-21",
            price="250",
            rating="Outperform",
            source="https://research.example.com/amd",
            report=report,
        )

        assert runner._persist_price_target(target) is True
        persisted_target = runner.service.upsert_target.call_args.args[1]
        assert persisted_target.report == report
        assert len(persisted_target.report.overview) == 1000
        assert len(persisted_target.report.key_takeaways) == 4
        assert len(persisted_target.report.key_takeaways[0]) == 500

        payload = discord_templates.price_target(target)
        description = payload["embeds"][0]["description"]
        assert len(description) == 3048
        assert "x" * 997 + "..." in description
        assert "t" * 497 + "..." in description
        assert "Fifth" not in description

    def test_price_target_defensively_truncates_discord_description(
        self,
        caplog,
    ):
        report = TargetReport.model_construct(
            overview="x" * 4096,
            key_takeaways=["Takeaway"],
        )
        target = Target(
            ticker="AMD",
            institution="Baird",
            date="2026-07-21",
            price="250",
            rating="Outperform",
            source="https://research.example.com/amd",
            report=report,
        )

        with caplog.at_level(
            logging.WARNING,
            logger="gemini.discord_templates",
        ):
            payload = discord_templates.price_target(target)

        description = payload["embeds"][0]["description"]
        assert len(description) == 4096
        assert description.endswith("...")
        assert (
            "Truncated Discord target report description for "
            "AMD / Baird / 2026-07-21 / $250"
        ) in caplog.text

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_failed_trusted_target_enrichment_is_not_persisted(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        runner.service.get_companies.return_value = {
            "AMD": make_company(
                "AMD",
                "26Q2",
                {"26Q2": make_quarter(quarter_id="26Q2")},
            ),
        }
        runner.service.get_institutions.return_value = {
            "baird": InstitutionRecord(
                name="Baird",
                aliases={"baird": "Baird"},
                enabled=True,
                trusted=True,
            ),
        }
        target = Target(
            ticker="AMD",
            institution="Baird",
            date="2026-07-21",
            price="250",
            rating="Outperform",
            source="https://research.example.com/amd",
        )
        runner.client.get_price_targets.return_value = Targets(
            targets=[target]
        )
        exception = RuntimeError("Gemini unavailable")
        runner.client.get_target_report.side_effect = exception

        runner.run()

        runner.service.upsert_target.assert_not_called()
        runner.discord.post_if_channel_exists.assert_not_called()
        runner.discord.post_eventlog.assert_not_called()
        runner.errors.report.assert_called_once_with(
            exception,
            logger=runner.log,
            source=runner.name,
            operation="retrieve_price_target_report",
            context={
                "ticker": "AMD",
                "institution": "Baird",
                "date": "2026-07-21",
            },
        )

    def test_price_target_uses_compact_ticker_channel_payload(self, runner):
        target = Target(
            ticker="AMD",
            institution="Baird",
            date="2026-07-24",
            price="1250",
            rating="Outperform",
            source="investing.com",
        )
        runner.discord.post_if_channel_exists.return_value = True

        runner._notify_price_target(target)

        runner.discord.post_if_channel_exists.assert_called_once_with(
            "AMD",
            {
                "embeds": [{
                    "title": "🎯 new price target $1250",
                    "color": 15844367,
                    "fields": [{
                        "name": "Baird",
                        "value": (
                            "Outperform\n"
                            "2026-07-24\n"
                            "source: investing.com"
                        ),
                        "inline": False,
                    }],
                }],
            },
        )
        runner.discord.post_eventlog.assert_not_called()
        runner.errors.report.assert_not_called()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_price_targets_skip_existing_and_response_duplicates(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        existing_target = CompanyTarget(
            institution="Important Research",
            date="2026-07-20",
            price="200",
            rating="Buy",
            source="https://existing.example.com/aapl",
        )
        company = make_company(
            "AAPL",
            "26Q2",
            {"26Q2": make_quarter(quarter_id="26Q2")},
            targets={"2026-07-20-existing": existing_target},
        )
        new_target = Target(
            ticker="AAPL",
            institution="New Research",
            date="2026-07-21",
            price="230",
            rating="Outperform",
            source="https://new.example.com/aapl",
        )
        runner.service.get_companies.return_value = {"AAPL": company}
        runner.client.get_price_targets.return_value = Targets(targets=[
            Target(
                ticker="AAPL",
                institution="IMPORTANT RESEARCH",
                date="2026-07-21",
                price="200",
                rating="Strong Buy",
                source="https://different.example.com/aapl",
            ),
            new_target,
            Target(
                ticker="AAPL",
                institution="NEW   RESEARCH",
                date="2026-07-21",
                price="230",
                rating="Buy",
                source="https://duplicate.example.com/aapl",
            ),
        ])

        runner.run()

        runner.service.upsert_target.assert_called_once_with(
            "AAPL",
            CompanyTarget(
                institution="New Research",
                date="2026-07-21",
                price="230",
                rating="Outperform",
                source="https://new.example.com/aapl",
            ),
        )
        runner.discord.post_eventlog.assert_called_once()
        assert (
            runner.discord.post_eventlog.call_args.args[0]["embeds"][0]["title"]
            == "🎯 AAPL | $230 | 2026-07-21"
        )
        runner.errors.report.assert_not_called()

    @pytest.mark.parametrize(
        ("days_old", "should_persist"),
        [
            (7, False),
            (8, True),
        ],
    )
    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_same_price_target_uses_seven_day_duplicate_window(
        self,
        mock_datetime,
        mock_is_past,
        runner,
        days_old,
        should_persist,
    ):
        today = date(2026, 7, 21)
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        existing_target = CompanyTarget(
            institution="Baird",
            date=today - timedelta(days=days_old),
            price="400",
            rating="Outperform",
            source="existing.example.com",
        )
        runner.service.get_companies.return_value = {
            "AAPL": make_company(
                "AAPL",
                "26Q2",
                {"26Q2": make_quarter(quarter_id="26Q2")},
                targets={"existing": existing_target},
            ),
        }
        runner.service.get_institutions.return_value = {
            "baird": InstitutionRecord(
                name="Baird",
                aliases={"baird": "Baird"},
                enabled=True,
            ),
        }
        runner.client.get_price_targets.return_value = Targets(targets=[
            Target(
                ticker="AAPL",
                institution="Baird",
                date=today,
                price="400",
                rating="Outperform",
                source="new.example.com",
            ),
        ])

        runner.run()

        if should_persist:
            runner.service.upsert_target.assert_called_once()
            runner.discord.post_eventlog.assert_called_once()
        else:
            runner.service.upsert_target.assert_not_called()
            runner.discord.post_eventlog.assert_not_called()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_changed_price_is_new_inside_duplicate_window(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        existing_target = CompanyTarget(
            institution="Baird",
            date="2026-07-20",
            price="400",
            rating="Outperform",
            source="existing.example.com",
        )
        runner.service.get_companies.return_value = {
            "AAPL": make_company(
                "AAPL",
                "26Q2",
                {"26Q2": make_quarter(quarter_id="26Q2")},
                targets={"existing": existing_target},
            ),
        }
        runner.service.get_institutions.return_value = {
            "baird": InstitutionRecord(
                name="Baird",
                aliases={"baird": "Baird"},
                enabled=True,
            ),
        }
        runner.client.get_price_targets.return_value = Targets(targets=[
            Target(
                ticker="AAPL",
                institution="Baird",
                date="2026-07-21",
                price="410",
                rating="Outperform",
                source="new.example.com",
            ),
        ])

        runner.run()

        runner.service.upsert_target.assert_called_once_with(
            "AAPL",
            CompanyTarget(
                institution="Baird",
                date="2026-07-21",
                price="410",
                rating="Outperform",
                source="new.example.com",
            ),
        )

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_newest_response_duplicate_is_persisted(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        runner.service.get_companies.return_value = {
            "AAPL": make_company(
                "AAPL",
                "26Q2",
                {"26Q2": make_quarter(quarter_id="26Q2")},
            ),
        }
        runner.service.get_institutions.return_value = {
            "baird": InstitutionRecord(
                name="Baird",
                aliases={"baird": "Baird"},
                enabled=True,
            ),
        }
        runner.client.get_price_targets.return_value = Targets(targets=[
            Target(
                ticker="AAPL",
                institution="Baird",
                date="2026-07-20",
                price="400",
                rating="Outperform",
                source="older.example.com",
            ),
            Target(
                ticker="AAPL",
                institution="Baird",
                date="2026-07-21",
                price="400",
                rating="Outperform",
                source="newer.example.com",
            ),
        ])

        runner.run()

        runner.service.upsert_target.assert_called_once_with(
            "AAPL",
            CompanyTarget(
                institution="Baird",
                date="2026-07-21",
                price="400",
                rating="Outperform",
                source="newer.example.com",
            ),
        )
        runner.discord.post_eventlog.assert_called_once()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_price_targets_are_not_requested_on_sunday(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 19)
        company = make_company(
            "AAPL",
            "26Q2",
            {"26Q2": make_quarter(quarter_id="26Q2")},
        )
        runner.service.get_companies.return_value = {"AAPL": company}

        runner.run()

        runner.client.get_price_targets.assert_not_called()
        runner.service.upsert_target.assert_not_called()
        runner.discord.post_eventlog.assert_not_called()

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
            make_initial_result(recovered_company),
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
        runner.discord.post_earnings.assert_not_called()

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
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(quarter_data)
        )
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
        reported_quarter = make_complete_quarter(
            report_date="2026-04-20",
            reported_eps="5.00",
        )
        next_quarter = make_quarter(
            quarter_id="26Q1",
            report_date="",
            ending_month="26-06",
            previous_report_date="2026-04-20",
        )
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(reported_quarter)
        )
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

    @pytest.mark.parametrize(
        ("value", "expected"),
        [
            ("16130", "16.13B"),
            ("-11033", "-11.03B"),
            ("1000", "1.0B"),
            ("-1000", "-1.0B"),
        ],
    )
    def test_format_financial_uses_absolute_magnitude(
        self,
        runner,
        value,
        expected,
    ):
        assert discord_templates.format_financial(value) == expected

    def test_quarter_payload_restores_webhook_identity(self, runner):
        payload = discord_templates.quarter_report(
            make_quarter(
                quarter_id="26Q2",
                reported_capex="75",
                reported_free_cash_flow="180",
            ),
            "AAPL",
        )

        assert payload["username"] == "Quarterly Results Reporter"
        assert payload["avatar_url"].endswith("/1390/1390704.png")
        financials = payload["embeds"][0]["fields"][0]["value"]
        assert "**CapEx:** 75.0M" in financials
        assert "**Free Cash Flow:** 180.0M" in financials

    @patch("gemini.retriever.datetime")
    def test_check_report_dates_next_week_logs_error_when_not_sunday(self, mock_datetime, runner):
        """Test Case: check_report_dates_next_week should log an error instead of posting when called on a non-Sunday."""
        mock_datetime.now.return_value = datetime(2026, 4, 27)  # Monday
        runner.check_report_dates_next_week(ReportDates(report_dates=[]))
        runner.log.error.assert_called_once()
        assert "should run on Sunday, but is Monday" in runner.log.error.call_args[0][0]
        runner.discord.post_earnings.assert_not_called()

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
        runner.discord.post_earnings.assert_called_once()
        payload = runner.discord.post_earnings.call_args.args[0]
        assert payload["username"] == "Quarterly Results Reporter"
        embed = payload["embeds"][0]
        fields = embed["fields"]
        assert embed["title"] == "📅 Upcoming Earnings Reports"
        assert fields[0]["name"] == "**Monday** (2026-04-27)"
        assert fields[0]["value"] == "AAPL - 25Q4"
        assert fields[-1]["name"] == "**Friday** (2026-05-01)"
        assert fields[-1]["value"] == "MSFT - 25Q4"
