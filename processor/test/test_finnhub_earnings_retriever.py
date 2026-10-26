import logging
from datetime import date
from decimal import Decimal
from unittest.mock import call, create_autospec

import pytest

from discord.client import DiscordClient
from error_reporting import ErrorReporter
from gemini.currency import QuarterCurrencyConverter
from gemini.models import Company as GeminiCompany
from gemini.models import FxRate
from gemini.models import Info as GeminiInfo
from gemini.models import Quarter as GeminiQuarter
from gemini.service import FirebaseService as GeminiFirebaseService
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


def make_runner():
    instance = object.__new__(FinnhubEarningsRetrieverRunner)
    instance.service = create_autospec(FirebaseService, instance=True)
    instance.gemini_service = create_autospec(
        GeminiFirebaseService,
        instance=True,
    )
    instance.gemini_service.get_companies.return_value = {}
    instance.currency_converter = create_autospec(
        QuarterCurrencyConverter,
        instance=True,
    )
    instance.client = create_autospec(FinnhubClient, instance=True)
    instance.discord = create_autospec(DiscordClient, instance=True)
    instance.log = create_autospec(logging.Logger, instance=True)
    instance.errors = create_autospec(ErrorReporter, instance=True)
    instance.discord_post_earnings = create_autospec(
        instance.discord_post_earnings
    )
    instance.almost_equals_earnings = create_autospec(
        instance.almost_equals_earnings
    )
    instance.sleeper = lambda _: None
    return instance


class TestFinnhubEarningsRetriever:
    @pytest.fixture
    def runner(self):
        return make_runner()

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
        error = runner.errors.report.call_args.args[0]
        assert str(error) == "API Error"
        runner.errors.report.assert_called_once_with(
            error,
            logger=runner.log,
            source=runner.name,
            operation="process_company",
            context={"company_id": "FAIL"},
        )
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
        runner.errors.report.assert_not_called()

    def test_run_logs_exception_when_loading_companies_fails(self, runner):
        """Test Case: Failure during company loading should be logged once and stop processing."""
        error = Exception("DB Error")
        runner.service.get_companies.side_effect = error
        runner.run()
        runner.client.get_earnings.assert_not_called()
        runner.errors.report.assert_called_once_with(
            error,
            logger=runner.log,
            source=runner.name,
            operation="run",
        )
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


    def test_process_company_directly_initializes_new_company(self, runner):
        """Test Case: process_company can be called standalone for a single
        new ticker, e.g. by the company-onboarding watcher."""
        earnings_dict = {"26Q1": make_earnings()}
        runner.client.get_earnings.return_value = earnings_dict

        runner.process_company("AAPL", None)

        runner.service.init_company.assert_called_once_with(
            "AAPL",
            earnings_dict,
        )
        runner.discord_post_earnings.assert_called_once()


def make_gemini_company(currency_override="$", currency="₩"):
    return GeminiCompany(
        info=GeminiInfo(
            ticker="SKHY",
            currency=currency,
            currency_override=currency_override,
            last_update="2026-10-23",
            current_quarter_id="26Q3",
        ),
        quarters={"26Q3": GeminiQuarter(
            name="Q3 2026",
            id="26Q3",
            ending_month="26-09",
            report_date_previous_quarter="2026-07-29",
            report_date_this_quarter="2026-10-28",
        )},
    )


class TestFinnhubAdsScaling:
    @pytest.fixture
    def runner(self):
        return make_runner()

    @staticmethod
    def adr_company(ratio):
        company = make_gemini_company(currency_override=None, currency="$")
        info = company.info.model_copy(update={"ads_per_share": ratio})
        return company.model_copy(update={"info": info})

    def test_eps_estimates_are_stored_per_ads(self, runner):
        runner.client.get_earnings.return_value = {
            "26Q3": make_earnings(epse="59.4749", epsa="60", reve=70_755_000_000),
        }

        runner.process_company("SKHY", None, self.adr_company(Decimal("10")))

        stored = runner.service.init_company.call_args.args[1]["26Q3"]
        assert stored.epse == Decimal("5.9475")
        assert stored.epsa == Decimal("6.0000")
        # Revenue is the company's, not per share.
        assert stored.reve == Decimal("70755000000")

    def test_missing_eps_stays_missing(self, runner):
        runner.client.get_earnings.return_value = {
            "26Q3": make_earnings(epse="50", epsa=None),
        }

        runner.process_company("SKHY", None, self.adr_company(Decimal("10")))

        stored = runner.service.init_company.call_args.args[1]["26Q3"]
        assert stored.epsa is None
        assert stored.epse == Decimal("5.0000")

    @pytest.mark.parametrize("ratio", [None, Decimal("1")])
    def test_no_ratio_means_no_scaling(self, runner, ratio):
        earnings = {"26Q3": make_earnings(epse="50")}
        runner.client.get_earnings.return_value = earnings

        runner.process_company("SKHY", None, self.adr_company(ratio))

        runner.service.init_company.assert_called_once_with("SKHY", earnings)

    def test_conversion_and_ads_scaling_combine(self, runner):
        # KRW per ordinary share -> USD -> per ADS.
        runner.currency_converter.rate_for_period.side_effect = (
            lambda start, end, **_kw: FxRate(rate=Decimal("1500"), source="polygon")
        )
        runner.currency_converter.convert_amounts.side_effect = (
            QuarterCurrencyConverter.convert_amounts.__get__(
                runner.currency_converter,
            )
        )
        runner.client.get_earnings.return_value = {
            "26Q3": make_earnings(epse="90000", reve=150_000_000_000_000),
        }
        company = make_gemini_company()
        company = company.model_copy(update={
            "info": company.info.model_copy(update={"ads_per_share": Decimal("10")}),
        })

        runner.process_company("SKHY", None, company)

        stored = runner.service.init_company.call_args.args[1]["26Q3"]
        assert stored.epse == Decimal("6.0000")
        assert stored.reve == Decimal("100000000000.0000")


class TestFinnhubCurrencyConversion:
    @pytest.fixture
    def runner(self):
        return make_runner()

    def convert_stub(self, runner):
        runner.currency_converter.rate_for_period.side_effect = (
            lambda start, end, **_kw: FxRate(
                rate=Decimal("1500"),
                source="polygon",
            )
        )
        runner.currency_converter.convert_amounts.side_effect = (
            QuarterCurrencyConverter.convert_amounts.__get__(
                runner.currency_converter,
            )
        )

    def test_estimates_are_stored_in_the_currency_override(self, runner):
        self.convert_stub(runner)
        runner.service.get_companies.return_value = {"SKHY": None}
        runner.gemini_service.get_companies.return_value = {
            "SKHY": make_gemini_company(),
        }
        runner.client.get_earnings.return_value = {
            "26Q3": make_earnings(epse="75000", reve="150000000000000"),
            "26Q4": make_earnings(epse="90000", reve="165000000000000"),
        }

        runner.run()

        stored = runner.service.init_company.call_args.args[1]
        assert stored["26Q3"].epse == Decimal("50.0000")
        assert stored["26Q3"].reve == Decimal("100000000000.0000")
        assert stored["26Q4"].epse == Decimal("60.0000")
        assert stored["26Q3"].epsa is None
        assert stored["26Q3"].report == "2026-04-27-bmo"
        runner.errors.report.assert_not_called()

    def test_each_quarter_uses_its_own_period(self, runner):
        self.convert_stub(runner)
        runner.client.get_earnings.return_value = {
            "26Q3": make_earnings(),
            "27Q1": make_earnings(),
        }

        runner.process_company("SKHY", None, make_gemini_company())

        periods = [
            (call.args[0], call.args[1])
            for call in runner.currency_converter.rate_for_period.call_args_list
        ]
        # 26Q3 is known to Gemini; 27Q1 falls back to calendar quarters.
        assert periods == [
            (date(2026, 7, 1), date(2026, 9, 30)),
            (date(2027, 1, 1), date(2027, 3, 31)),
        ]
        assert all(
            call.kwargs == {"from_currency": "₩", "to_currency": "$"}
            for call in runner.currency_converter.rate_for_period.call_args_list
        )

    def test_company_without_currency_override_is_untouched(self, runner):
        earnings = {"26Q3": make_earnings()}
        runner.client.get_earnings.return_value = earnings

        runner.process_company(
            "ASML",
            None,
            make_gemini_company(currency_override=None, currency="€"),
        )

        runner.currency_converter.rate_for_period.assert_not_called()
        runner.service.init_company.assert_called_once_with("ASML", earnings)

    def test_failed_conversion_stores_nothing(self, runner):
        runner.service.get_companies.return_value = {"SKHY": None}
        runner.gemini_service.get_companies.return_value = {
            "SKHY": make_gemini_company(),
        }
        runner.client.get_earnings.return_value = {"26Q3": make_earnings()}
        runner.currency_converter.rate_for_period.side_effect = RuntimeError(
            "no rates"
        )

        runner.run()

        runner.service.init_company.assert_not_called()
        runner.service.init_quarter.assert_not_called()
        runner.service.new_earnings.assert_not_called()
        runner.discord_post_earnings.assert_not_called()
        runner.errors.report.assert_called_once()
        assert str(runner.errors.report.call_args.args[0]) == "no rates"

    def test_conversion_without_a_configured_converter_is_an_error(
        self,
        runner,
    ):
        runner.currency_converter = None
        runner.client.get_earnings.return_value = {"26Q3": make_earnings()}

        with pytest.raises(ValueError, match="no currency converter"):
            runner.process_company("SKHY", None, make_gemini_company())

        runner.service.init_company.assert_not_called()

    def test_unreadable_gemini_companies_stop_the_run(self, runner):
        runner.service.get_companies.return_value = {"SKHY": None}
        runner.gemini_service.get_companies.side_effect = Exception("db down")

        runner.run()

        runner.client.get_earnings.assert_not_called()
        runner.service.init_company.assert_not_called()
        runner.errors.report.assert_called_once()


def make_real_runner():
    runner = object.__new__(FinnhubEarningsRetrieverRunner)
    runner.discord = create_autospec(DiscordClient, instance=True)
    runner.discord.post_if_channel_exists.return_value = False
    return runner


def test_discord_payload_preserves_zero_actual_values():
    runner = make_real_runner()
    earnings = make_earnings(epsa=0, reva=0)

    runner.discord_post_earnings("AAPL", "26Q1", None, earnings)

    runner.discord.post_eventlog.assert_called_once()
    payload = runner.discord.post_eventlog.call_args.args[0]
    assert payload["username"] == "Earnings Estimates Reporter"
    assert payload["embeds"][0]["title"].startswith("📊 AAPL | 26Q1")
    reported = payload["embeds"][0]["fields"][1]
    assert reported["name"] == "Reported:"
    assert "earnings: \u200b 0.00" in reported["value"]
    assert "revenues: \u200b 0.00M" in reported["value"]


def test_discord_payload_includes_partially_available_actuals():
    runner = make_real_runner()
    earnings = make_earnings(epsa=Decimal("0.25"), reva=None)

    runner.discord_post_earnings("AAPL", "26Q1", None, earnings)

    runner.discord.post_eventlog.assert_called_once()
    payload = runner.discord.post_eventlog.call_args.args[0]
    reported = payload["embeds"][0]["fields"][1]
    assert "earnings: \u200b 0.25" in reported["value"]


def test_discord_payload_uses_existing_ticker_channel():
    runner = make_real_runner()
    runner.discord.post_if_channel_exists.return_value = True
    earnings = make_earnings()

    runner.discord_post_earnings("AAPL", "26Q1", None, earnings)

    runner.discord.post_if_channel_exists.assert_called_once()
    channel_name, payload = (
        runner.discord.post_if_channel_exists.call_args.args
    )
    assert channel_name == "AAPL"
    assert payload["embeds"][0]["title"].startswith("📊 26Q1 |")
    assert "AAPL" not in payload["embeds"][0]["title"]
    assert "username" not in payload
    assert "avatar_url" not in payload
    runner.discord.post_eventlog.assert_not_called()


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
