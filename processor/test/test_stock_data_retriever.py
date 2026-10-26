import logging
from datetime import date, datetime
from decimal import Decimal
from unittest.mock import create_autospec, patch

import pytest

from discord.client import DiscordClient, ThreadedPost
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
    FxRate,
    Info,
    InstitutionRating,
    InstitutionRatingDimension,
    InstitutionResolution,
    InstitutionResolutions,
    InstitutionRecord,
    Quarter,
    ReportDate,
    ReportDates,
    Target,
    TargetCandidate,
    TargetCandidates,
    TargetReport,
)
from gemini.service import FirebaseService
from gemini.adr import AdsRatio, AdsRatioDetector
from gemini.currency import QuarterCurrencyConverter
from gemini.institutions import InstitutionRegistry
from gemini.prices import PriceRange, QuarterPriceRanges
from gemini.retriever import StockDataRetrieverRunner
from indicators.models import PriceIndicator
from indicators.service import FirebaseService as IndicatorsFirebaseService
from myfinnhub.models import Company as FinnhubCompany
from myfinnhub.models import Earnings
from myfinnhub.models import Quarter as FinnhubQuarter
from myfinnhub.service import FirebaseService as FinnhubFirebaseService
from sec.client import SecClient
from sec.financials import QuarterValues


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
    currency_override=None,
):
    return Company(
        info=Info(
            ticker=ticker,
            currency=currency,
            currency_override=currency_override,
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


def make_earnings(report="2026-08-04", **overrides):
    data = {"epsa": None, "epse": None, "reva": None, "reve": None}
    data.update(overrides)
    return Earnings(report=report, **data)


class TestStockDataRetriever:
    @pytest.fixture
    def runner(self):
        instance = object.__new__(StockDataRetrieverRunner)
        instance.client = create_autospec(GeminiClient, instance=True)
        instance.service = create_autospec(FirebaseService, instance=True)
        instance.finnhub_service = create_autospec(
            FinnhubFirebaseService,
            instance=True,
        )
        instance.indicators_service = create_autospec(
            IndicatorsFirebaseService,
            instance=True,
        )
        instance.discord = create_autospec(
            DiscordClient,
            instance=True,
        )
        instance.currency_converter = create_autospec(
            QuarterCurrencyConverter,
            instance=True,
        )
        instance.log = create_autospec(logging.Logger, instance=True)
        instance.errors = create_autospec(ErrorReporter, instance=True)
        instance.client.get_price_targets.return_value = TargetCandidates(
            targets=[]
        )
        instance.service.get_institutions.return_value = {}
        instance.finnhub_service.get_companies.return_value = {}
        instance.indicators_service.get_prices.return_value = {}
        instance.discord.post_if_channel_exists.return_value = None
        instance.discord.post_with_thread_if_channel_exists.return_value = None
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

    def test_onboard_company_initializes_and_retrieves_targets_on_success(
        self,
        runner,
    ):
        quarter = make_quarter(quarter_id="26Q1")
        mock_company = make_company("AAPL", "26Q1", {"26Q1": quarter})
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(mock_company)
        )
        runner._retrieve_price_targets = create_autospec(
            runner._retrieve_price_targets
        )

        result = runner.onboard_company("AAPL")

        assert result == mock_company
        runner.service.init_company.assert_called_once_with(
            id="AAPL",
            data=mock_company,
        )
        runner._retrieve_price_targets.assert_called_once_with(
            {"AAPL": mock_company},
            lookback_days=30,
        )

    def test_onboard_company_skips_targets_when_initialization_fails(
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
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(
                company,
                errors=["26Q2 revenue unavailable."],
                raw_response='{"errors":["revenue unavailable"]}',
            )
        )
        runner._retrieve_price_targets = create_autospec(
            runner._retrieve_price_targets
        )

        result = runner.onboard_company("ASML")

        assert result is None
        runner.service.init_company.assert_not_called()
        runner._retrieve_price_targets.assert_not_called()

    def test_usd_optional_fields_are_warned_and_persisted(
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
            "ORCL",
            "26Q3",
            {
                "26Q3": current_quarter,
                "26Q2": reported_quarter,
            },
        )
        response = make_initial_result(
            company,
            errors=[
                "26Q2 reported dividend was unavailable.",
            ],
            raw_response='{"errors":["unavailable data"]}',
        )
        runner.service.get_companies.return_value = {"ORCL": None}
        runner.client.get_initial_stock_data.return_value = response

        runner.run()

        runner.service.init_company.assert_called_once_with(
            id="ORCL",
            data=company,
        )
        runner.errors.report_error_message.assert_not_called()
        runner.errors.report_warning_message.assert_called_once()
        warning_call = runner.errors.report_warning_message.call_args
        assert warning_call.kwargs["context"] == {
            "company_id": "ORCL",
            "currency": "$",
            "error_count": 2,
        }
        assert "26Q2 reported dividend was unavailable" in warning_call.args[0]
        assert "26Q2.reported_div" in warning_call.args[0]
        assert response.raw_response in warning_call.args[0]

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
        runner.service.report_quarter.assert_called_once_with("NVDA", new_reported_quarter, None)
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
    def test_company_with_currency_override_is_stored_converted(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        company = make_company(
            "SKHY",
            "25Q4",
            {"25Q4": make_quarter(report_date="2026-04-20")},
            currency="₩",
            currency_override="$",
        )
        runner.service.get_companies.return_value = {"SKHY": company}
        reported = make_complete_quarter(report_date="2026-04-20")
        converted = reported.model_copy(
            update={"reported_revenues": Decimal("1")},
        )
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(reported)
        )
        fx_rate = FxRate(rate=Decimal("1500"), source="polygon")
        runner.currency_converter.convert.return_value = (converted, fx_rate)
        runner.compose_new_quarter = create_autospec(
            runner.compose_new_quarter,
            return_value=make_quarter(quarter_id="26Q1", report_date=""),
        )

        runner.run()

        # Gemini is asked in the company's own currency...
        runner.client.get_quarter_report.assert_called_once_with(
            "SKHY",
            company.quarters["25Q4"],
            "₩",
        )
        runner.currency_converter.convert.assert_called_once_with(
            reported,
            from_currency="₩",
            to_currency="$",
        )
        # ...and the converted quarter is what is stored and posted.
        runner.service.report_quarter.assert_called_once_with(
            "SKHY",
            converted,
            fx_rate,
        )
        runner.service.create_quarter.assert_called_once()
        runner.errors.report_error_message.assert_not_called()

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_failed_conversion_stores_nothing_and_is_retried_later(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        company = make_company(
            "SKHY",
            "25Q4",
            {"25Q4": make_quarter(report_date="2026-04-20")},
            currency="₩",
            currency_override="$",
        )
        runner.service.get_companies.return_value = {"SKHY": company}
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(
                make_complete_quarter(report_date="2026-04-20"),
                raw_response="raw gemini response",
            )
        )
        runner.currency_converter.convert.side_effect = RuntimeError(
            "Only 3 daily USD/KRW rates"
        )

        runner.run()

        runner.service.report_quarter.assert_not_called()
        runner.service.create_quarter.assert_not_called()
        runner.discord.post_earnings.assert_not_called()
        runner.errors.report_error_message.assert_called_once()
        message = runner.errors.report_error_message.call_args.args[0]
        assert "Converting the quarter to $ failed" in message
        assert "Only 3 daily USD/KRW rates" in message

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_conversion_without_a_configured_converter_is_an_error(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        runner.currency_converter = None
        company = make_company(
            "SKHY",
            "25Q4",
            {"25Q4": make_quarter(report_date="2026-04-20")},
            currency="₩",
            currency_override="$",
        )
        runner.service.get_companies.return_value = {"SKHY": company}
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(
                make_complete_quarter(report_date="2026-04-20"),
            )
        )

        runner.run()

        runner.service.report_quarter.assert_not_called()
        runner.errors.report_error_message.assert_called_once()
        assert "no currency converter" in (
            runner.errors.report_error_message.call_args.args[0]
        )

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_company_without_currency_override_is_not_converted(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        company = make_company(
            "ASML",
            "25Q4",
            {"25Q4": make_quarter(report_date="2026-04-20")},
            currency="€",
        )
        runner.service.get_companies.return_value = {"ASML": company}
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(
                make_complete_quarter(report_date="2026-04-20"),
            )
        )
        runner.compose_new_quarter = create_autospec(
            runner.compose_new_quarter,
            return_value=make_quarter(quarter_id="26Q1", report_date=""),
        )

        runner.run()

        runner.currency_converter.convert.assert_not_called()
        runner.service.report_quarter.assert_called_once()
        assert runner.service.report_quarter.call_args.args[2] is None

    @staticmethod
    def _initial_company(currency="$", latest_revenue="1000"):
        quarters = {
            "26Q3": make_quarter("26Q3", "2026-10-28", ending_month="26-09",
                                 previous_report_date="2026-07-29"),
            "26Q2": make_quarter("26Q2", "2026-07-29", ending_month="26-06",
                                 previous_report_date="2026-04-29",
                                 reported_revenues=latest_revenue,
                                 reported_net_income="200"),
            "26Q1": make_quarter("26Q1", "2026-04-29", ending_month="26-03",
                                 previous_report_date="2026-01-29"),
            "25Q4": make_quarter("25Q4", "2026-01-29", ending_month="25-12",
                                 previous_report_date="2025-10-29"),
            "25Q3": make_quarter("25Q3", "2025-10-29", ending_month="25-09",
                                 previous_report_date="2025-07-29"),
        }
        return make_company("ABC", "26Q3", quarters, currency=currency)

    @pytest.fixture
    def sourced(self, runner):
        runner.sec_client = create_autospec(SecClient, instance=True)
        runner.sec_client.get_cik.return_value = "0000012345"
        runner.sec_client.get_company_facts.return_value = {"facts": {}}
        runner.price_ranges = create_autospec(QuarterPriceRanges, instance=True)
        runner.price_ranges.ranges.side_effect = lambda ticker, windows: {
            quarter_id: PriceRange(Decimal("10"), Decimal("20"))
            for quarter_id in windows
        }
        return runner

    def test_older_quarters_come_from_sec_and_prices_from_polygon(self, sourced):
        sourced.client.get_initial_stock_data.return_value = (
            make_initial_result(self._initial_company())
        )
        sec_values = {
            "26Q2": QuarterValues(
                values={"reported_revenues": Decimal("1"),
                        "reported_shares": Decimal("7.5")},
                estimated={"reported_shares"},
            ),
            "26Q1": QuarterValues(values={"reported_revenues": Decimal("900"),
                                          "reported_net_income": Decimal("90")}),
            "25Q4": QuarterValues(
                values={"reported_revenues": Decimal("800"),
                        "reported_net_income": Decimal("80"),
                        "reported_eps": Decimal("1.5")},
                estimated={"reported_eps"},
            ),
            "25Q3": QuarterValues(values={"reported_revenues": Decimal("700"),
                                          "reported_net_income": Decimal("70")}),
        }

        with (
            patch("gemini.retriever.sec_financials.has_quarterly_filings",
                  return_value=True),
            patch("gemini.retriever.sec_financials.extract_quarters",
                  return_value=sec_values) as extract,
        ):
            company = sourced._initialize_company("ABC")

        sourced.client.get_initial_stock_data.assert_called_once_with(
            "ABC", financials_from_sec=True
        )
        extract.assert_called_once_with(
            {"facts": {}},
            {
                "26Q2": "26-06",
                "26Q1": "26-03",
                "25Q4": "25-12",
                "25Q3": "25-09",
            },
        )
        assert company.quarters["26Q1"].reported_revenues == Decimal("900")
        assert company.quarters["25Q3"].reported_net_income == Decimal("70")
        # The latest reported quarter keeps Gemini's figures; SEC only
        # fills what was left empty, and an estimate there is marked too.
        assert company.quarters["26Q2"].reported_revenues == Decimal("1000")
        assert company.quarters["26Q2"].reported_shares == Decimal("7.5")
        assert company.info.cik == "0000012345"
        assert company.info.estimates == {
            "25Q4": ["reported_eps"],
            "26Q2": ["reported_shares"],
        }
        # Prices of every reported quarter, never the unreported one.
        for quarter_id in ("26Q2", "26Q1", "25Q4", "25Q3"):
            assert company.quarters[quarter_id].price_min == Decimal("10")
            assert company.quarters[quarter_id].price_max == Decimal("20")
        assert company.quarters["26Q3"].price_min is None
        windows = sourced.price_ranges.ranges.call_args.args[1]
        assert windows["26Q2"] == (date(2026, 4, 29), date(2026, 7, 29))
        assert "26Q3" not in windows
        sourced.service.init_company.assert_called_once_with(
            id="ABC", data=company
        )

    def test_foreign_filers_keep_gemini_for_every_quarter(self, sourced):
        sourced.client.get_initial_stock_data.return_value = (
            make_initial_result(self._initial_company())
        )

        with (
            patch("gemini.retriever.sec_financials.has_quarterly_filings",
                  return_value=False),
            patch("gemini.retriever.sec_financials.extract_quarters") as extract,
        ):
            company = sourced._initialize_company("ABC")

        sourced.client.get_initial_stock_data.assert_called_once_with("ABC")
        extract.assert_not_called()
        assert company.info.cik == "0000012345"
        assert company.info.estimates == {}

    def test_a_failing_sec_lookup_falls_back_to_gemini_with_a_warning(
        self,
        sourced,
    ):
        sourced.sec_client.get_company_facts.side_effect = RuntimeError("403")
        sourced.client.get_initial_stock_data.return_value = (
            make_initial_result(self._initial_company())
        )

        company = sourced._initialize_company("ABC")

        sourced.client.get_initial_stock_data.assert_called_once_with("ABC")
        assert company is not None
        assert company.info.cik is None
        warning = next(
            c for c in sourced.errors.report_warning_message.call_args_list
            if c.kwargs["operation"] == "initialize_company_sec"
        )
        assert "SEC lookup failed for ABC" in warning.args[0]

    def test_a_company_unknown_to_sec_is_initialized_without_a_cik(
        self,
        sourced,
    ):
        sourced.sec_client.get_cik.return_value = None
        sourced.client.get_initial_stock_data.return_value = (
            make_initial_result(self._initial_company())
        )

        company = sourced._initialize_company("ABC")

        sourced.sec_client.get_company_facts.assert_not_called()
        assert company.info.cik is None

    def test_prices_stay_with_gemini_when_not_traded_in_dollars(self, sourced):
        company = self._initial_company(currency="€")
        company.quarters["26Q2"] = company.quarters["26Q2"].model_copy(
            update={"price_min": Decimal("590"), "price_max": Decimal("900")}
        )
        sourced.client.get_initial_stock_data.return_value = (
            make_initial_result(company)
        )

        result = sourced._initialize_company("ABC")

        sourced.price_ranges.ranges.assert_not_called()
        assert result.quarters["26Q2"].price_min == Decimal("590")

    def test_the_override_currency_decides_where_prices_come_from(
        self,
        sourced,
    ):
        company = self._initial_company(currency="₩")
        company = company.model_copy(update={
            "info": company.info.model_copy(update={"currency_override": "$"}),
        })
        sourced.client.get_initial_stock_data.return_value = (
            make_initial_result(company)
        )

        result = sourced._initialize_company("ABC")

        sourced.price_ranges.ranges.assert_called_once()
        assert result.quarters["26Q2"].price_max == Decimal("20")

    def test_a_polygon_failure_is_a_warning_and_initialization_goes_on(
        self,
        sourced,
    ):
        sourced.price_ranges.ranges.side_effect = RuntimeError("429")
        sourced.client.get_initial_stock_data.return_value = (
            make_initial_result(self._initial_company())
        )

        company = sourced._initialize_company("ABC")

        assert company is not None
        sourced.service.init_company.assert_called_once()
        messages = [
            c.args[0]
            for c in sourced.errors.report_warning_message.call_args_list
        ]
        assert any("Price ranges from Polygon failed: 429" in m for m in messages)

    def test_only_the_latest_reported_quarter_must_have_its_core_figures(
        self,
        runner,
    ):
        # The older quarters carry no revenue or net income at all.
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(self._initial_company())
        )

        company = runner._initialize_company("ABC")

        assert company is not None
        runner.service.init_company.assert_called_once()
        runner.errors.report_error_message.assert_not_called()

    def test_a_latest_quarter_without_core_figures_still_blocks_init(
        self,
        runner,
    ):
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(self._initial_company(latest_revenue=None))
        )

        assert runner._initialize_company("ABC") is None

        runner.service.init_company.assert_not_called()
        message = runner.errors.report_error_message.call_args.args[0]
        assert "26Q2.reported_revenues" in message

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_dollar_company_quarter_report_takes_prices_from_polygon(
        self,
        mock_datetime,
        mock_is_past,
        sourced,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        company = make_company(
            "NVDA", "25Q4", {"25Q4": make_quarter(report_date="2026-04-20")}
        )
        sourced.service.get_companies.return_value = {"NVDA": company}
        reported = make_complete_quarter(
            report_date="2026-04-20", price_min=None, price_max=None
        )
        sourced.client.get_quarter_report.return_value = (
            make_quarter_report_result(reported)
        )
        sourced.compose_new_quarter = create_autospec(
            sourced.compose_new_quarter,
            return_value=make_quarter(quarter_id="26Q1", report_date=""),
        )

        sourced.run()

        sourced.client.get_quarter_report.assert_called_once_with(
            "NVDA", company.quarters["25Q4"], "$", include_prices=False
        )
        windows = sourced.price_ranges.ranges.call_args.args[1]
        assert windows == {"25Q4": (date(2026, 1, 20), date(2026, 4, 20))}
        stored = sourced.service.report_quarter.call_args.args[1]
        assert (stored.price_min, stored.price_max) == (
            Decimal("10"), Decimal("20")
        )
        sourced.errors.report_error_message.assert_not_called()

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_polygon_failure_holds_the_quarter_report_for_a_retry(
        self,
        mock_datetime,
        mock_is_past,
        sourced,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        company = make_company(
            "NVDA", "25Q4", {"25Q4": make_quarter(report_date="2026-04-20")}
        )
        sourced.service.get_companies.return_value = {"NVDA": company}
        sourced.client.get_quarter_report.return_value = (
            make_quarter_report_result(
                make_complete_quarter(report_date="2026-04-20"),
                raw_response="raw",
            )
        )
        sourced.price_ranges.ranges.side_effect = RuntimeError("polygon down")

        sourced.run()

        sourced.service.report_quarter.assert_not_called()
        sourced.service.create_quarter.assert_not_called()
        sourced.discord.post_earnings.assert_not_called()
        message = sourced.errors.report_error_message.call_args.args[0]
        assert "Retrieving the price range from Polygon failed: polygon down" in message

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_non_dollar_company_quarter_report_still_asks_gemini_for_prices(
        self,
        mock_datetime,
        mock_is_past,
        sourced,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        company = make_company(
            "ASML", "25Q4", {"25Q4": make_quarter(report_date="2026-04-20")},
            currency="€",
        )
        sourced.service.get_companies.return_value = {"ASML": company}
        sourced.client.get_quarter_report.return_value = (
            make_quarter_report_result(
                make_complete_quarter(report_date="2026-04-20"),
            )
        )
        sourced.compose_new_quarter = create_autospec(
            sourced.compose_new_quarter,
            return_value=make_quarter(quarter_id="26Q1", report_date=""),
        )

        sourced.run()

        sourced.client.get_quarter_report.assert_called_once_with(
            "ASML", company.quarters["25Q4"], "€"
        )
        sourced.price_ranges.ranges.assert_not_called()

    def test_init_detects_the_ads_ratio_and_stores_per_ads_figures(self, runner):
        runner.ads_detector = create_autospec(AdsRatioDetector, instance=True)
        runner.ads_detector.detect.return_value = AdsRatio(
            ads_per_share=Decimal("10"),
            ads_shares=Decimal("7288665960"),
            ordinary_shares=Decimal("730490000"),
        )
        company = self._initial_company()
        company.quarters["26Q2"] = company.quarters["26Q2"].model_copy(
            update={"reported_eps": Decimal("50"), "reported_shares": Decimal("730")}
        )
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(company)
        )

        result = runner._initialize_company("ABC")

        assert result.info.ads_per_share == Decimal("10")
        assert result.info.ads_checked_on == date.today()
        assert result.quarters["26Q2"].reported_eps == Decimal("5")
        assert result.quarters["26Q2"].reported_shares == Decimal("7300")
        assert result.quarters["26Q2"].reported_revenues == Decimal("1000")

    def test_init_without_an_adr_leaves_per_share_figures_alone(self, runner):
        runner.ads_detector = create_autospec(AdsRatioDetector, instance=True)
        runner.ads_detector.detect.return_value = None
        company = self._initial_company()
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(company)
        )

        result = runner._initialize_company("ABC")

        assert result.info.ads_per_share is None
        assert result.info.ads_checked_on is None

    def test_a_failing_ads_detection_is_a_warning_not_a_blocker(self, runner):
        runner.ads_detector = create_autospec(AdsRatioDetector, instance=True)
        runner.ads_detector.detect.side_effect = RuntimeError("429")
        runner.client.get_initial_stock_data.return_value = (
            make_initial_result(self._initial_company())
        )

        result = runner._initialize_company("ABC")

        assert result is not None
        messages = [
            c.args[0]
            for c in runner.errors.report_warning_message.call_args_list
        ]
        assert any("Detecting the ADS ratio failed: 429" in m for m in messages)

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_quarter_report_of_an_adr_is_stored_per_ads(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        mock_is_past.return_value = True
        company = make_company(
            "SKHY", "25Q4", {"25Q4": make_quarter(report_date="2026-04-20")}
        )
        company = company.model_copy(update={
            "info": company.info.model_copy(
                update={"ads_per_share": Decimal("10")}
            ),
        })
        runner.service.get_companies.return_value = {"SKHY": company}
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(
                make_complete_quarter(
                    report_date="2026-04-20",
                    reported_eps="50",
                    reported_shares="730",
                ),
            )
        )
        runner.compose_new_quarter = create_autospec(
            runner.compose_new_quarter,
            return_value=make_quarter(quarter_id="26Q1", report_date=""),
        )

        runner.run()

        stored = runner.service.report_quarter.call_args.args[1]
        assert stored.reported_eps == Decimal("5")
        assert stored.reported_shares == Decimal("7300")
        assert stored.reported_revenues == Decimal("1000")

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
            None,
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
            "missing_field_count": 5,
        }
        # Gross profit and operating income are optional (banks and
        # insurers have neither), so their absence is never reported as
        # missing. Numbered list lines only: the raw Gemini response
        # that follows legitimately contains the keys.
        assert ". reported_gross_profit" not in warning_call.args[0]
        assert ". reported_operating_income" not in warning_call.args[0]
        assert ". reported_eps" in warning_call.args[0]
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

    @patch("utils.is_past_date", return_value=True)
    @patch("gemini.retriever.datetime")
    def test_usd_quarter_report_accepts_missing_capex_and_free_cash_flow(
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
        reported_quarter = make_complete_quarter(
            report_date="2026-04-20",
            reported_capex=None,
            reported_free_cash_flow=None,
        )
        runner.service.get_companies.return_value = {"NVDA": company}
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(reported_quarter)
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
            "NVDA",
            reported_quarter,
            None,
        )
        runner.service.create_quarter.assert_called_once_with(
            "NVDA",
            next_quarter,
        )
        runner.errors.report_error_message.assert_not_called()
        runner.errors.report_warning_message.assert_not_called()

    @pytest.mark.parametrize(
        "missing_fields",
        [
            ("reported_gross_profit",),
            ("reported_operating_income",),
            ("reported_gross_profit", "reported_operating_income"),
        ],
        ids=["gross-profit", "operating-income", "both"],
    )
    @patch("utils.is_past_date", return_value=True)
    @patch("gemini.retriever.datetime")
    def test_usd_quarter_report_accepts_missing_profit_lines(
        self,
        mock_datetime,
        mock_is_past,
        runner,
        missing_fields,
    ):
        # Banks and insurers (JPMorgan) have no gross profit or
        # operating income; neither may fail the report or warn.
        mock_datetime.now.return_value = datetime(2026, 4, 27)
        current_quarter = make_quarter(report_date="2026-04-20")
        company = make_company("JPM", "25Q4", {"25Q4": current_quarter})
        reported_quarter = make_complete_quarter(
            report_date="2026-04-20",
            **dict.fromkeys(missing_fields),
        )
        runner.service.get_companies.return_value = {"JPM": company}
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(reported_quarter)
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
            "JPM",
            reported_quarter,
            None,
        )
        runner.errors.report_error_message.assert_not_called()
        runner.errors.report_warning_message.assert_not_called()

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
        target = TargetCandidate(
            ticker="AAPL",
            institution="Important Research",
            date="2026-07-20",
            price="225.50",
            rating="Outperform",
            source="https://research.example.com/aapl",
        )
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[target]
        )
        rating_dimension = InstitutionRatingDimension(
            score="4.0/10",
            description="Widely distributed but limited execution weight.",
        )
        runner.client.resolve_new_institutions.return_value = (
            InstitutionResolutions(resolutions=[
                InstitutionResolution(
                    institution="Important Research",
                    is_alias=False,
                    institutional_weight=rating_dimension,
                    media_shock_value=rating_dimension,
                ),
            ])
        )

        runner.run()

        runner.client.get_price_targets.assert_called_once_with(
            ["AAPL"],
            date(2026, 7, 19),
            date(2026, 7, 21),
        )
        runner.client.resolve_new_institutions.assert_called_once_with(
            ["Important Research"],
            [],
        )
        runner.service.create_institutions.assert_called_once_with({
            "important-research": InstitutionRecord(
                name="Important Research",
                aliases={
                    "important-research": "Important Research",
                },
                enabled=True,
                rating=InstitutionRating(
                    institutional_weight=rating_dimension,
                    media_shock_value=rating_dimension,
                ),
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
        assert runner.discord.post_eventlog.call_count == 2
        new_institution_payload = (
            runner.discord.post_eventlog.call_args_list[0].args[0]
        )
        assert (
            new_institution_payload["embeds"][0]["title"]
            == "🏦 New institutions"
        )
        assert (
            new_institution_payload["embeds"][0]["description"]
            == "Important Research"
        )
        payload = runner.discord.post_eventlog.call_args_list[1].args[0]
        assert payload["username"] == "Institutional Price Target Reporter"
        assert payload["avatar_url"].endswith("/1872/1872505.png")
        embed = payload["embeds"][0]
        assert (
            embed["title"]
            == "🎯 AAPL | Price target $225.50 | Important Research"
        )
        assert embed["fields"] == [{
            "name": "​",
            "value": (
                "Outperform\n"
                "2026-07-20\n"
                "source: https://research.example.com/aapl"
            ),
            "inline": False,
        }]
        runner.client.get_target_report.assert_not_called()
        runner.errors.report.assert_not_called()

    def make_resolved_target(self, institution: InstitutionRecord):
        target = Target(
            ticker="AAPL",
            institution=institution.name,
            date="2026-07-20",
            price="225.50",
            source="https://research.example.com/aapl",
        )
        return (target, institution)

    def test_reconcile_new_institutions_attaches_ratings_for_distinct_institutions(
        self,
        runner,
    ):
        institutions = InstitutionRegistry({})
        candidate = institutions.resolve_or_create("Important Research")
        resolved_targets = [self.make_resolved_target(candidate)]
        dimension = InstitutionRatingDimension(
            score="7.0/10",
            description="Solid institutional distribution.",
        )
        runner.client.resolve_new_institutions.return_value = (
            InstitutionResolutions(resolutions=[
                InstitutionResolution(
                    institution="Important Research",
                    is_alias=False,
                    institutional_weight=dimension,
                    media_shock_value=dimension,
                ),
            ])
        )

        result = runner._reconcile_new_institutions(
            resolved_targets,
            institutions,
        )

        runner.client.resolve_new_institutions.assert_called_once_with(
            ["Important Research"],
            [],
        )
        assert result == resolved_targets
        assert institutions.new_institutions[
            "important-research"
        ].rating == InstitutionRating(
            institutional_weight=dimension,
            media_shock_value=dimension,
        )
        runner.service.add_institution_alias.assert_not_called()

    def test_reconcile_new_institutions_merges_alias_into_existing_institution(
        self,
        runner,
    ):
        existing = InstitutionRecord(
            name="JPMorgan",
            aliases={"jpmorgan": "JPMorgan"},
            enabled=True,
            trusted=True,
        )
        institutions = InstitutionRegistry({"jpmorgan": existing})
        candidate = institutions.resolve_or_create("JPM Securities")
        resolved_targets = [self.make_resolved_target(candidate)]
        runner.client.resolve_new_institutions.return_value = (
            InstitutionResolutions(resolutions=[
                InstitutionResolution(
                    institution="JPM Securities",
                    is_alias=True,
                    alias_of="JPMorgan",
                ),
            ])
        )

        result = runner._reconcile_new_institutions(
            resolved_targets,
            institutions,
        )

        runner.client.resolve_new_institutions.assert_called_once_with(
            ["JPM Securities"],
            ["JPMorgan"],
        )
        assert "jpm-securities" not in institutions.new_institutions
        runner.service.add_institution_alias.assert_called_once_with(
            institution_id="jpmorgan",
            alias_key="jpm-securities",
            alias_name="JPM Securities",
        )
        assert len(result) == 1
        remapped_target, remapped_institution = result[0]
        assert remapped_institution is existing
        assert remapped_target.institution == "JPMorgan"

    def test_reconcile_new_institutions_leaves_unmatched_candidate_untouched(
        self,
        runner,
    ):
        institutions = InstitutionRegistry({})
        candidate = institutions.resolve_or_create("Important Research")
        resolved_targets = [self.make_resolved_target(candidate)]
        runner.client.resolve_new_institutions.return_value = (
            InstitutionResolutions(resolutions=[])
        )

        result = runner._reconcile_new_institutions(
            resolved_targets,
            institutions,
        )

        assert result == resolved_targets
        assert institutions.new_institutions["important-research"] is (
            candidate
        )
        runner.service.add_institution_alias.assert_not_called()

    def test_reconcile_new_institutions_leaves_candidate_unrated_when_gemini_omits_scores(
        self,
        runner,
    ):
        """Regression test: is_alias=false with missing rating scores
        (Gemini not honoring the "required when is_alias is false"
        instruction) must not crash - the candidate is just left unrated,
        same as any other unresolved candidate."""
        institutions = InstitutionRegistry({})
        candidate = institutions.resolve_or_create("Important Research")
        resolved_targets = [self.make_resolved_target(candidate)]
        runner.client.resolve_new_institutions.return_value = (
            InstitutionResolutions(resolutions=[
                InstitutionResolution(
                    institution="Important Research",
                    is_alias=False,
                ),
            ])
        )

        result = runner._reconcile_new_institutions(
            resolved_targets,
            institutions,
        )

        assert result == resolved_targets
        assert institutions.new_institutions[
            "important-research"
        ].rating is None
        runner.service.add_institution_alias.assert_not_called()

    def test_reconcile_new_institutions_falls_back_on_gemini_failure(
        self,
        runner,
    ):
        institutions = InstitutionRegistry({})
        candidate = institutions.resolve_or_create("Important Research")
        resolved_targets = [self.make_resolved_target(candidate)]
        error = RuntimeError("Gemini unavailable")
        runner.client.resolve_new_institutions.side_effect = error

        result = runner._reconcile_new_institutions(
            resolved_targets,
            institutions,
        )

        assert result == resolved_targets
        assert institutions.new_institutions["important-research"] is (
            candidate
        )
        runner.errors.report.assert_called_once_with(
            error,
            logger=runner.log,
            source=runner.name,
            operation="resolve_new_institutions",
            context={"institutions": "Important Research"},
        )

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
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[
                TargetCandidate(
                    ticker="AAPL",
                    institution="BofA Securities",
                    date="2026-07-21",
                    price="225",
                    rating="Buy",
                    source="new.example.com",
                ),
            ]
        )

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
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[
                TargetCandidate(
                    ticker="AAPL",
                    institution="Robert W. Baird",
                    date="2026-07-21",
                    price="230",
                    rating="Outperform",
                    source="new.example.com",
                ),
            ]
        )

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
        assert payload["embeds"][0]["title"].endswith("| Baird")

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
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[
                TargetCandidate(
                    ticker="AAPL",
                    institution="Rosenblatt Securities",
                    date="2026-07-21",
                    price="230",
                    rating="Buy",
                    source="new.example.com",
                ),
            ]
        )

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
        candidate = TargetCandidate(
            ticker="AMD",
            institution="Baird",
            date="2026-07-21",
            price="250",
            rating="Outperform",
            source="https://research.example.com/amd",
        )
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
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[candidate]
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
        assert embed["title"] == "🎯 AMD | Price target $250 | Baird"
        assert embed["fields"] == [{
            "name": "​",
            "value": (
                "Outperform\n2026-07-21\nsource: https://research.example.com/amd"
            ),
            "inline": False,
        }]
        assert embed["description"] == (
            "**Overview**\n"
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
        assert len(description) == 3047
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
    def test_failed_trusted_target_enrichment_falls_back_to_unenriched_target(
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
        candidate = TargetCandidate(
            ticker="AMD",
            institution="Baird",
            date="2026-07-21",
            price="250",
            rating="Outperform",
            source="https://research.example.com/amd",
        )
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[candidate]
        )
        exception = RuntimeError("Gemini unavailable")
        runner.client.get_target_report.side_effect = exception

        runner.run()

        runner.service.upsert_target.assert_called_once_with(
            "AMD",
            CompanyTarget(
                institution="Baird",
                date="2026-07-21",
                price="250",
                rating="Outperform",
                source="https://research.example.com/amd",
            ),
        )
        runner.discord.post_eventlog.assert_called_once()
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

    @staticmethod
    def _researched_target():
        return Target(
            ticker="AMD",
            institution="Baird",
            date="2026-07-24",
            price="1250",
            rating="Outperform",
            source="investing.com",
            report=TargetReport(
                overview="Baird expects stronger data-center demand.",
                key_takeaways=["The price target was increased to $1250."],
            ),
        )

    @staticmethod
    def _plain_entries():
        rating = InstitutionRating(
            institutional_weight=InstitutionRatingDimension(
                score="8.5/10",
                description="Moves significant institutional capital.",
            ),
            media_shock_value=InstitutionRatingDimension(
                score="4.0/10",
                description="Rarely drives retail headlines.",
            ),
        )
        baird = Target(
            ticker="AAPL",
            institution="Baird",
            date="2026-07-24",
            price="250",
            rating="Outperform",
            source="investing.com",
        )
        wedbush = Target(
            ticker="AAPL",
            institution="Wedbush",
            date="2026-07-24",
            price="190",
            source="wedbush.example.com",
        )
        return [
            (baird, InstitutionRecord(
                name="Baird",
                aliases={"baird": "Baird"},
                rating=rating,
            )),
            (wedbush, InstitutionRecord(
                name="Wedbush",
                aliases={"wedbush": "Wedbush"},
            )),
        ]

    def test_targets_post_a_list_with_details_in_a_thread(
        self,
        runner,
    ):
        entries = self._plain_entries()
        runner.discord.post_with_thread_if_channel_exists.return_value = (
            ThreadedPost(url="https://discord.com/channels/g/c/m")
        )

        runner._notify_price_targets("AAPL", entries, Decimal("200"))

        call = runner.discord.post_with_thread_if_channel_exists.call_args
        channel, listing = call.args
        assert channel == "AAPL"
        assert listing == {
            "embeds": [{
                "title": "🎯 New price targets",
                "color": 15844367,
                "description": (
                    "Baird: $250 *(+25.0%)*\nWedbush: $190 *(-5.0%)*"
                ),
            }],
        }
        assert call.kwargs["thread_name"] == "AAPL · Baird, Wedbush - details"
        baird_message, wedbush_message = call.kwargs["thread_payloads"]
        # Each institution's details look like the old single-target post.
        assert baird_message == {
            "embeds": [{
                "title": "🎯 Price target $250 | Baird",
                "color": 15844367,
                "description": "+25.0% upside from latest close ($200)",
                "fields": [{
                    "name": "​",
                    "value": (
                        "Outperform\n"
                        "2026-07-24\n"
                        "source: investing.com\n"
                        "\n"
                        "Institutional Weight: 8.5/10 — Moves "
                        "significant institutional capital.\n"
                        "Media Shock Value: 4.0/10 — Rarely drives "
                        "retail headlines."
                    ),
                    "inline": False,
                }],
            }],
        }
        wedbush_embed = wedbush_message["embeds"][0]
        assert wedbush_embed["title"] == "🎯 Price target $190 | Wedbush"
        assert wedbush_embed["description"] == (
            "-5.0% downside from latest close ($200)"
        )
        assert wedbush_embed["fields"][0]["value"] == (
            "Not provided\n2026-07-24\nsource: wedbush.example.com"
        )
        runner.discord.post_if_channel_exists.assert_not_called()
        runner.discord.post_eventlog.assert_not_called()
        runner.errors.report.assert_not_called()
        runner.errors.report_warning_message.assert_not_called()

    def test_single_target_is_posted_as_a_list_too(self, runner):
        entries = self._plain_entries()[:1]
        runner.discord.post_with_thread_if_channel_exists.return_value = (
            ThreadedPost(url="https://discord.com/channels/g/c/m")
        )

        runner._notify_price_targets("AAPL", entries)

        call = runner.discord.post_with_thread_if_channel_exists.call_args
        assert call.args[1]["embeds"][0]["title"] == (
            "🎯 New price targets"
        )
        assert call.args[1]["embeds"][0]["description"] == "Baird: $250"
        assert call.kwargs["thread_name"] == "AAPL · Baird - details"
        assert len(call.kwargs["thread_payloads"]) == 1

    def test_trusted_and_untrusted_targets_share_one_list_and_thread(
        self,
        runner,
    ):
        researched = self._researched_target().model_copy(
            update={"ticker": "AAPL", "price": Decimal("250")},
        )
        baird = InstitutionRecord(
            name="Baird",
            aliases={"baird": "Baird"},
            trusted=True,
        )
        plain = self._plain_entries()[1]
        runner.discord.post_with_thread_if_channel_exists.return_value = (
            ThreadedPost(url="https://discord.com/channels/g/c/m")
        )

        runner._notify_price_targets(
            "AAPL",
            [(researched, baird), plain],
            Decimal("200"),
        )

        runner.discord.post_with_thread_if_channel_exists.assert_called_once()
        call = runner.discord.post_with_thread_if_channel_exists.call_args
        # Trusted institutions are bold; the list keeps the given order.
        assert call.args[1]["embeds"][0]["description"] == (
            "**Baird: $250** *(+25.0%)*\nWedbush: $190 *(-5.0%)*"
        )
        assert call.kwargs["thread_name"] == "AAPL · Baird, Wedbush - details"
        trusted_message, untrusted_message = call.kwargs["thread_payloads"]
        # The trusted target's thread message is the full report post.
        trusted_embed = trusted_message["embeds"][0]
        assert trusted_embed["title"] == "🎯 Price target $250 | Baird"
        assert trusted_embed["description"].startswith(
            "+25.0% upside from latest close ($200)\n\n**Overview**\n"
            "Baird expects stronger data-center demand."
        )
        assert "• The price target was increased to $1250." in (
            trusted_embed["description"]
        )
        assert trusted_embed["fields"][0]["value"].startswith(
            "Outperform\n2026-07-24"
        )
        assert untrusted_message["embeds"][0]["title"] == (
            "🎯 Price target $190 | Wedbush"
        )
        runner.discord.post_eventlog.assert_not_called()
        runner.errors.report.assert_not_called()
        runner.errors.report_warning_message.assert_not_called()

    def test_researched_targets_without_a_channel_are_posted_inline(
        self,
        runner,
    ):
        researched = self._researched_target()
        runner.discord.post_with_thread_if_channel_exists.return_value = None

        runner._notify_price_targets(
            "AMD",
            [(researched, InstitutionRecord(name="Baird"))],
        )

        embed = runner.discord.post_eventlog.call_args.args[0]["embeds"][0]
        assert embed["title"] == "🎯 AMD | Price target $1250 | Baird"
        assert "**Overview**" in embed["description"]

    def test_targets_without_a_channel_fall_back_to_eventlog(
        self,
        runner,
    ):
        entries = self._plain_entries()
        runner.discord.post_with_thread_if_channel_exists.return_value = None

        runner._notify_price_targets("AAPL", entries[:1])
        runner._notify_price_targets("AAPL", entries)

        single, several = (
            call.args[0]["embeds"][0]
            for call in runner.discord.post_eventlog.call_args_list
        )
        # One target keeps its full inline post, several the list.
        assert single["title"] == "🎯 AAPL | Price target $250 | Baird"
        assert single["fields"][0]["value"].startswith("Outperform")
        assert several["title"] == "🎯 AAPL | New price targets"
        runner.discord.post_if_channel_exists.assert_not_called()

    def test_undelivered_thread_is_reported_as_a_warning(
        self,
        runner,
    ):
        entries = self._plain_entries()
        runner.discord.post_with_thread_if_channel_exists.return_value = (
            ThreadedPost(
                url="https://discord.com/channels/g/c/m",
                thread_error="thread creation failed (Missing Access)",
            )
        )

        runner._notify_price_targets("AAPL", entries)

        runner.errors.report_warning_message.assert_called_once()
        warning = runner.errors.report_warning_message.call_args
        assert "Missing Access" in warning.args[0]
        assert warning.kwargs["context"] == {"ticker": "AAPL", "count": "2"}
        runner.errors.report.assert_not_called()
        runner.discord.post_eventlog.assert_not_called()

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
        new_target = TargetCandidate(
            ticker="AAPL",
            institution="New Research",
            date="2026-07-21",
            price="230",
            rating="Outperform",
            source="https://new.example.com/aapl",
        )
        runner.service.get_companies.return_value = {"AAPL": company}
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[
                TargetCandidate(
                    ticker="AAPL",
                    institution="IMPORTANT RESEARCH",
                    date="2026-07-21",
                    price="200",
                    rating="Strong Buy",
                    source="https://different.example.com/aapl",
                ),
                new_target,
                TargetCandidate(
                    ticker="AAPL",
                    institution="NEW   RESEARCH",
                    date="2026-07-21",
                    price="230",
                    rating="Buy",
                    source="https://duplicate.example.com/aapl",
                ),
            ]
        )

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
        assert runner.discord.post_eventlog.call_count == 2
        assert (
            runner.discord.post_eventlog.call_args.args[0]["embeds"][0]["title"]
            == "🎯 AAPL | Price target $230 | New Research"
        )
        runner.errors.report.assert_not_called()

    @pytest.mark.parametrize(
        ("existing_target_date", "should_persist"),
        [
            # The quarter boundary itself (report_date_previous_quarter)
            # still belongs to the previous quarter, so a same-quarter
            # match to it never happens - not a duplicate.
            ("2026-01-20", True),
            # The day after the boundary is the first day of the current
            # quarter - a same institution/price match here is a
            # duplicate no matter how many days have since passed.
            ("2026-01-21", False),
            ("2026-07-14", False),
        ],
    )
    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_same_price_target_is_duplicate_within_current_quarter_only(
        self,
        mock_datetime,
        mock_is_past,
        runner,
        existing_target_date,
        should_persist,
    ):
        today = date(2026, 7, 21)
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        existing_target = CompanyTarget(
            institution="Baird",
            date=existing_target_date,
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
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[
                TargetCandidate(
                    ticker="AAPL",
                    institution="Baird",
                    date=today,
                    price="400",
                    rating="Outperform",
                    source="new.example.com",
                ),
            ]
        )

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
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[
                TargetCandidate(
                    ticker="AAPL",
                    institution="Baird",
                    date="2026-07-21",
                    price="410",
                    rating="Outperform",
                    source="new.example.com",
                ),
            ]
        )

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
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[
                TargetCandidate(
                    ticker="AAPL",
                    institution="Baird",
                    date="2026-07-20",
                    price="400",
                    rating="Outperform",
                    source="older.example.com",
                ),
                TargetCandidate(
                    ticker="AAPL",
                    institution="Baird",
                    date="2026-07-21",
                    price="400",
                    rating="Outperform",
                    source="newer.example.com",
                ),
            ]
        )

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
        runner.service.report_quarter.assert_called_once_with("NVDA", reported_quarter, None)
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

    def test_compose_new_quarter_keeps_calendar_year_on_fiscal_label_rollover(
        self,
        runner,
    ):
        """Regression test for a bug where a company whose fiscal-year label
        runs ahead of the calendar year (e.g. MSFT, whose fiscal Q4 ends in
        June) got the fiscal-id year copied onto ending_month's calendar
        year on the Q4-to-Q1 rollover, producing a +15 month jump (27-09)
        instead of the correct +3 months (26-09)."""
        previous_quarter = make_quarter(
            quarter_id="26Q4",
            name="Q4 2026",
            ending_month="26-06",
            previous_report_date="2026-01-20",
            report_date="2026-04-20",
        )
        new_quarter = runner.compose_new_quarter(previous_quarter)
        assert new_quarter.id == "27Q1"
        assert new_quarter.name == "Q1 2027"
        assert new_quarter.ending_month == "26-09"

    def test_compose_new_quarter_keeps_calendar_year_without_label_rollover(
        self,
        runner,
    ):
        """Regression test for a bug where, once a company's fiscal-id year
        outran its ending_month's calendar year (e.g. NVDA), even a
        same-fiscal-year Q1-to-Q2 step re-derived the calendar year from the
        fiscal id instead of from the previous ending_month, producing
        27-07 instead of the correct 26-07."""
        previous_quarter = make_quarter(
            quarter_id="27Q1",
            name="Q1 2027",
            ending_month="26-04",
            previous_report_date="2025-08-20",
            report_date="2025-11-20",
        )
        new_quarter = runner.compose_new_quarter(previous_quarter)
        assert new_quarter.id == "27Q2"
        assert new_quarter.name == "Q2 2027"
        assert new_quarter.ending_month == "26-07"

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
        assert "CapEx: 75.0M" in financials
        assert "Free Cash Flow: 180.0M" in financials

    def test_quarter_report_shows_dash_for_missing_capex_fcf_divs_shares(
        self,
        runner,
    ):
        payload = discord_templates.quarter_report(
            make_quarter(quarter_id="26Q2"),
            "AAPL",
        )

        financials = payload["embeds"][0]["fields"][0]["value"]
        assert "CapEx: -" in financials
        assert "Free Cash Flow: -" in financials
        assert "Divs: -" in financials
        assert "Shares: -" in financials

    def test_quarter_report_shows_qoq_and_yoy_percent_change(self, runner):
        current_quarter = make_complete_quarter(
            quarter_id="26Q2",
            reported_revenues="1000",
            reported_eps="1.25",
        )
        qoq_quarter = make_complete_quarter(
            quarter_id="26Q1",
            reported_revenues="800",
            reported_eps="1.00",
        )
        yoy_quarter = make_complete_quarter(
            quarter_id="25Q2",
            reported_revenues="500",
            reported_eps="0.50",
        )

        payload = discord_templates.quarter_report(
            current_quarter,
            "AAPL",
            qoq_quarter=qoq_quarter,
            yoy_quarter=yoy_quarter,
        )

        financials = payload["embeds"][0]["fields"][0]["value"]
        assert "Revenue: 1.0B *(+25.0%, +100%)*" in financials
        assert "EPS: 1.25" in financials

    def test_quarter_report_drops_decimal_at_100_percent_threshold(
        self,
        runner,
    ):
        baseline = make_quarter(quarter_id="26Q1", reported_net_income="100")

        under_100 = make_quarter(
            quarter_id="26Q2", reported_net_income="199"
        )
        payload = discord_templates.quarter_report(
            under_100, "AAPL", qoq_quarter=baseline,
        )
        financials = payload["embeds"][0]["fields"][0]["value"]
        assert "Net Income: 199.0M *(+99.0% QoQ)*" in financials

        at_100 = make_quarter(quarter_id="26Q2", reported_net_income="200")
        payload = discord_templates.quarter_report(
            at_100, "AAPL", qoq_quarter=baseline,
        )
        financials = payload["embeds"][0]["fields"][0]["value"]
        assert "Net Income: 200.0M *(+100% QoQ)*" in financials

    def test_quarter_report_change_uses_abs_denominator_for_negative_baseline(
        self,
        runner,
    ):
        # A loss narrowing from -100 to -50 is an improvement; the naive
        # (new - old) / old formula would show it as a decline because the
        # baseline is negative.
        current_quarter = make_quarter(
            quarter_id="26Q2",
            reported_net_income="-50",
        )
        qoq_quarter = make_quarter(
            quarter_id="26Q1",
            reported_net_income="-100",
        )

        payload = discord_templates.quarter_report(
            current_quarter,
            "AAPL",
            qoq_quarter=qoq_quarter,
        )

        financials = payload["embeds"][0]["fields"][0]["value"]
        assert "Net Income: -50.0M *(+50.0% QoQ)*" in financials

    def test_quarter_report_omits_yoy_when_comparison_quarter_unavailable(
        self,
        runner,
    ):
        current_quarter = make_complete_quarter(
            quarter_id="26Q2",
            reported_revenues="1000",
        )
        qoq_quarter = make_complete_quarter(
            quarter_id="26Q1",
            reported_revenues="800",
        )

        payload = discord_templates.quarter_report(
            current_quarter,
            "AAPL",
            qoq_quarter=qoq_quarter,
            yoy_quarter=None,
        )

        financials = payload["embeds"][0]["fields"][0]["value"]
        assert "Revenue: 1.0B *(+25.0% QoQ)*" in financials
        assert "YoY" not in financials

    def test_quarter_report_margins_in_percentage_points(self, runner):
        current_quarter = make_complete_quarter(
            quarter_id="26Q2",
            reported_revenues="1000",
            reported_gross_profit="500",
            reported_operating_income="300",
            reported_net_income="200",
        )
        qoq_quarter = make_complete_quarter(
            quarter_id="26Q1",
            reported_revenues="800",
            reported_gross_profit="320",
            reported_operating_income="160",
            reported_net_income="80",
        )

        payload = discord_templates.quarter_report(
            current_quarter,
            "AAPL",
            qoq_quarter=qoq_quarter,
        )

        fields_by_name = {
            field["name"]: field["value"]
            for field in payload["embeds"][0]["fields"]
        }
        assert fields_by_name["Margins"] == (
            "Gross Margin: 50.0% *(+10.0pp QoQ)*\n"
            "Oper. Margin: 30.0% *(+10.0pp QoQ)*\n"
            "Net Margin: 20.0% *(+10.0pp QoQ)*"
        )

    def test_quarter_report_skips_margin_when_revenue_is_nonpositive(
        self,
        runner,
    ):
        current_quarter = make_quarter(
            quarter_id="26Q2",
            reported_revenues="0",
            reported_net_income="-50",
        )

        payload = discord_templates.quarter_report(current_quarter, "AAPL")

        field_names = {
            field["name"] for field in payload["embeds"][0]["fields"]
        }
        assert "Margins" not in field_names

    def test_quarter_report_vs_estimates_shows_beat_and_miss(self, runner):
        current_quarter = make_quarter(
            quarter_id="26Q2",
            reported_eps="1.38",
            reported_revenues="11536",
        )
        # reve is Finnhub's raw-dollar estimate (millions * 1,000,000).
        estimates = make_earnings(epse="1.25", reve="11180000000")

        payload = discord_templates.quarter_report(
            current_quarter,
            "AAPL",
            estimates=estimates,
        )

        fields_by_name = {
            field["name"]: field["value"]
            for field in payload["embeds"][0]["fields"]
        }
        assert fields_by_name["vs. Estimates"] == (
            "EPS: est. 1.25 *(beat +10.4%)*\n"
            "Revenue: est. 11.18B *(beat +3.2%)*"
        )

    def test_quarter_report_omits_estimates_field_when_unavailable(
        self,
        runner,
    ):
        current_quarter = make_quarter(quarter_id="26Q2")

        payload = discord_templates.quarter_report(current_quarter, "AAPL")

        field_names = {
            field["name"] for field in payload["embeds"][0]["fields"]
        }
        assert "vs. Estimates" not in field_names

    @pytest.mark.parametrize(
        ("quarter_id", "quarters_back", "expected"),
        [
            ("26Q2", 1, "26Q1"),
            ("26Q1", 1, "25Q4"),
            ("26Q4", 1, "26Q3"),
            ("26Q2", 4, "25Q2"),
            ("26Q1", 4, "25Q1"),
        ],
    )
    def test_quarter_id_offset(
        self,
        runner,
        quarter_id,
        quarters_back,
        expected,
    ):
        assert (
            StockDataRetrieverRunner._quarter_id_offset(
                quarter_id,
                quarters_back,
            )
            == expected
        )

    @patch("utils.is_past_date")
    @patch("gemini.retriever.datetime")
    def test_reporting_includes_qoq_yoy_and_estimates_from_history(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 8, 4)
        mock_is_past.return_value = True
        qoq_quarter = make_complete_quarter(
            quarter_id="26Q1",
            report_date="2026-05-05",
            reported_revenues="800",
        )
        yoy_quarter = make_complete_quarter(
            quarter_id="25Q2",
            report_date="2025-08-05",
            reported_revenues="500",
        )
        current_quarter = make_quarter(
            quarter_id="26Q2",
            report_date="2026-08-04",
            previous_report_date="2026-05-05",
        )
        company = make_company(
            "AAPL",
            "26Q2",
            {
                "26Q1": qoq_quarter,
                "25Q2": yoy_quarter,
                "26Q2": current_quarter,
            },
        )
        runner.service.get_companies.return_value = {"AAPL": company}
        reported_quarter = make_complete_quarter(
            quarter_id="26Q2",
            report_date="2026-08-04",
            previous_report_date="2026-05-05",
            reported_revenues="1000",
        )
        runner.client.get_quarter_report.return_value = (
            make_quarter_report_result(reported_quarter)
        )
        runner.compose_new_quarter = create_autospec(
            runner.compose_new_quarter,
            return_value=make_quarter(
                quarter_id="26Q3",
                report_date="",
                previous_report_date="2026-08-04",
            ),
        )
        finnhub_company = FinnhubCompany(root={
            "26Q2": FinnhubQuarter(root={
                "20260801": make_earnings(
                    "2026-08-01", epse="1.20", reve="950000000",
                ),
            }),
        })
        runner.finnhub_service.get_companies.return_value = {
            "AAPL": finnhub_company,
        }

        runner.run()

        payload = runner.discord.post_earnings.call_args.args[0]
        fields_by_name = {
            field["name"]: field["value"]
            for field in payload["embeds"][0]["fields"]
        }
        assert "**Financials** (QoQ/YoY)" in fields_by_name
        assert "vs. Estimates" in fields_by_name

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

    def test_single_target_shows_upside_to_latest_close_below_the_title(
        self,
        runner,
    ):
        target = Target(
            ticker="MU",
            institution="Baird",
            date="2026-10-05",
            price="1250",
            rating="Outperform",
            source="investing.com",
        )

        embed = discord_templates.ticker_price_target(
            target,
            close=Decimal("1074.89"),
        )["embeds"][0]

        assert embed["description"] == (
            "+16.3% upside from latest close ($1074.89)"
        )

    def test_target_below_the_latest_close_shows_a_negative_percent(
        self,
        runner,
    ):
        target = Target(
            ticker="MU",
            institution="Baird",
            date="2026-10-05",
            price="1000",
            source="investing.com",
        )

        embed = discord_templates.price_target(
            target,
            close=Decimal("1074.89"),
        )["embeds"][0]

        assert embed["description"] == "-7.0% downside from latest close ($1074.89)"

    def test_price_line_comes_before_the_research_report(self, runner):
        target = Target(
            ticker="AMD",
            institution="Baird",
            date="2026-10-05",
            price="250",
            source="investing.com",
            report=TargetReport(
                overview="Baird expects stronger demand.",
                key_takeaways=["The target was raised."],
            ),
        )

        embed = discord_templates.ticker_price_target(
            target,
            close=Decimal("200"),
        )["embeds"][0]

        assert embed["description"] == (
            "+25.0% upside from latest close ($200)\n\n"
            "**Overview**\nBaird expects stronger demand.\n\n"
            "**Key takeaways**\n• The target was raised.\n\n\u200b"
        )

    def test_single_target_has_no_description_without_a_close(self, runner):
        target = Target(
            ticker="AMD",
            institution="Baird",
            date="2026-10-05",
            price="250",
            source="investing.com",
        )

        embed = discord_templates.ticker_price_target(target)["embeds"][0]

        assert "description" not in embed

    def test_aggregate_target_lines_end_with_an_italic_percent_bracket(
        self,
        runner,
    ):
        targets = [
            Target(
                ticker="AMD",
                institution="Baird",
                date="2026-10-05",
                price="250",
                source="a.example.com",
            ),
            Target(
                ticker="AMD",
                institution="Wedbush",
                date="2026-10-05",
                price="190",
                source="b.example.com",
            ),
            Target(
                ticker="AMD",
                institution="Rosenblatt",
                date="2026-10-05",
                price="450",
                source="c.example.com",
            ),
        ]

        with_close = discord_templates.ticker_price_targets(
            targets,
            close=Decimal("200"),
        )["embeds"][0]["description"]
        without_close = discord_templates.ticker_price_targets(targets)[
            "embeds"
        ][0]["description"]

        assert with_close == (
            "Baird: $250 *(+25.0%)*\n"
            "Wedbush: $190 *(-5.0%)*\n"
            "Rosenblatt: $450 *(+125%)*"
        )
        assert without_close == (
            "Baird: $250\nWedbush: $190\nRosenblatt: $450"
        )

    def _run_single_target(self, runner):
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
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[
                TargetCandidate(
                    ticker="AAPL",
                    institution="Baird",
                    date="2026-07-21",
                    price="230",
                    rating="Outperform",
                    source="new.example.com",
                ),
            ]
        )
        runner.run()
        return runner.discord.post_eventlog.call_args.args[0]["embeds"][0]

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_price_posted_only_when_retrieved_on_the_run_date(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        runner.indicators_service.get_prices.return_value = {
            "AAPL": PriceIndicator(value=Decimal("200"), retrieved_on=date(2026, 7, 21)),
        }

        embed = self._run_single_target(runner)

        runner.indicators_service.get_prices.assert_called_once_with(["AAPL"])
        assert embed["description"] == "+15.0% upside from latest close ($200)"

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_stale_price_is_left_out(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        runner.indicators_service.get_prices.return_value = {
            "AAPL": PriceIndicator(value=Decimal("200"), retrieved_on=date(2026, 7, 20)),
        }

        embed = self._run_single_target(runner)

        assert "description" not in embed

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_missing_price_is_left_out(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)

        embed = self._run_single_target(runner)

        assert "description" not in embed

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_price_lookup_failure_does_not_block_the_target_post(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        failure = RuntimeError("firebase unavailable")
        runner.indicators_service.get_prices.side_effect = failure

        embed = self._run_single_target(runner)

        assert "description" not in embed
        runner.errors.report.assert_called_once_with(
            failure,
            logger=runner.log,
            source=runner.name,
            operation="retrieve_latest_closes",
            context={"count": "1"},
        )

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_no_price_lookup_when_nothing_is_posted(
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

        runner.run()

        runner.indicators_service.get_prices.assert_not_called()

    @patch("utils.is_past_date", return_value=False)
    @patch("gemini.retriever.datetime")
    def test_aggregate_post_gets_percent_brackets_from_todays_price(
        self,
        mock_datetime,
        mock_is_past,
        runner,
    ):
        mock_datetime.now.return_value = datetime(2026, 7, 21)
        runner.indicators_service.get_prices.return_value = {
            "AAPL": PriceIndicator(value=Decimal("200"), retrieved_on=date(2026, 7, 21)),
        }
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
            "wedbush": InstitutionRecord(
                name="Wedbush",
                aliases={"wedbush": "Wedbush"},
                enabled=True,
            ),
        }
        runner.client.get_price_targets.return_value = TargetCandidates(
            targets=[
                TargetCandidate(
                    ticker="AAPL",
                    institution=institution,
                    date="2026-07-21",
                    price=price,
                    source=f"{institution.lower()}.example.com",
                )
                for institution, price in (("Baird", "230"), ("Wedbush", "190"))
            ]
        )

        runner.run()

        embed = runner.discord.post_eventlog.call_args.args[0]["embeds"][0]
        assert embed["title"] == "🎯 AAPL | New price targets"
        assert embed["description"] == (
            "Baird: $230 *(+15.0%)*\nWedbush: $190 *(-5.0%)*"
        )
