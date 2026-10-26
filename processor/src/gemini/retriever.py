import calendar
import logging
from collections.abc import Mapping
from datetime import date, datetime, timedelta
from dataclasses import dataclass
from decimal import Decimal

import utils
from discord.client import DiscordClient, ThreadedPost
from error_reporting import ErrorReporter
from gemini import discord_templates
from gemini.adr import AdsRatioDetector, scale_to_ads
from gemini.client import GeminiClient
from gemini.currency import QuarterCurrencyConverter
from gemini.institutions import (
    InstitutionRegistry,
    normalize_institution_name,
)
from gemini.models import (
    Company,
    CompanyTarget,
    FxRate,
    Info,
    InstitutionRating,
    InstitutionRecord,
    Quarter,
    ReportDate,
    ReportDates,
    Target,
    TargetCandidate,
)
from gemini.prices import (
    QuarterPriceRanges,
    prices_come_from_polygon,
)
from gemini.service import FirebaseService
from gemini.strings import ErrorMsg, LogMsg
from indicators.service import FirebaseService as IndicatorsFirebaseService
from myfinnhub.models import Company as FinnhubCompany
from myfinnhub.models import Earnings as FinnhubEarnings
from myfinnhub.service import FirebaseService as FinnhubFirebaseService
from sec import financials as sec_financials
from sec.client import SecClient

RUNNER_NAME = "StockDataRetriever"
PRICE_TARGET_LOOKBACK_DAYS = 2
PRICE_TARGET_ONBOARDING_LOOKBACK_DAYS = 30
REPORTED_QUARTER_DATA_FIELDS = (
    "reported_eps",
    "reported_revenues",
    "reported_gross_profit",
    "reported_operating_income",
    "reported_net_income",
    "reported_capex",
    "reported_free_cash_flow",
    "reported_div",
    "reported_shares",
    "price_min",
    "price_max",
)
REQUIRED_REPORTED_QUARTER_FIELDS = frozenset({
    "reported_revenues",
    "reported_net_income",
})
# Gross profit and operating income are optional because banks and
# insurers (JPMorgan) have none; requiring them made Gemini copy revenues
# or pre-tax income into them just to get the report accepted.
OPTIONAL_QUARTER_REPORT_FIELDS = frozenset({
    "reported_capex",
    "reported_free_cash_flow",
    "reported_gross_profit",
    "reported_operating_income",
})
# A quarterly filing newer than this means SEC has usable quarters.
SEC_QUARTERLY_FILING_MAX_AGE_DAYS = 700
logger = logging.getLogger(RUNNER_NAME)


@dataclass(frozen=True)
class SecFacts:
    cik: str
    facts: dict
    # Domestic filers have 10-Q data; foreign private issuers do not.
    quarterly: bool


class StockDataRetrieverRunner:
    log = logger
    name = RUNNER_NAME
    # model = "gemini-3-flash-preview"
    model = "gemini-3.1-pro-preview"
    # Optional collaborators: without them the older quarters' financials
    # and every price range come from Gemini alone.
    sec_client: SecClient | None = None
    price_ranges: QuarterPriceRanges | None = None
    ads_detector: AdsRatioDetector | None = None

    def __init__(
        self,
        gemini_api_key: str | None = None,
        client: GeminiClient | None = None,
        service: FirebaseService | None = None,
        finnhub_service: FinnhubFirebaseService | None = None,
        indicators_service: IndicatorsFirebaseService | None = None,
        discord: DiscordClient | None = None,
        error_reporter: ErrorReporter | None = None,
        currency_converter: QuarterCurrencyConverter | None = None,
        sec_client: SecClient | None = None,
        price_ranges: QuarterPriceRanges | None = None,
        ads_detector: AdsRatioDetector | None = None,
    ) -> None:
        self.errors = error_reporter or ErrorReporter(environment="local")
        self.ads_detector = ads_detector
        self.currency_converter = currency_converter
        self.sec_client = sec_client
        self.price_ranges = price_ranges
        if client is None:
            if gemini_api_key is None:
                raise ValueError("gemini_api_key is required without a client")
            client = GeminiClient(
                api_key=gemini_api_key,
                model=self.model,
                error_reporter=self.errors,
            )
        if discord is None:
            raise ValueError("discord is required")

        self.client = client
        self.service = (
            service
            if service is not None
            else FirebaseService(error_reporter=self.errors)
        )
        self.finnhub_service = finnhub_service or FinnhubFirebaseService(
            error_reporter=self.errors,
        )
        self.indicators_service = (
            indicators_service
            or IndicatorsFirebaseService(error_reporter=self.errors)
        )
        self.discord = discord

    def run(self):
        try:
            companies: dict = self.service.get_companies()
            finnhub_companies = self.finnhub_service.get_companies()
            report_dates = ReportDates(report_dates=list())
            for company_id in companies:
                try:
                    company = companies.get(company_id)
                    if self._needs_initialization(company):
                        company = self._initialize_company(company_id)
                        companies[company_id] = company
                    else:
                        current_quarter: Quarter = company.quarters.get(company.info.current_quarter_id)
                        if current_quarter is None:
                            self.log.error(ErrorMsg.QUARTER_NOT_FOUND.format(quarter_id=company.info.current_quarter_id, company_id=company_id))
                        else:
                            if utils.is_past_date(date=current_quarter.report_date_this_quarter):
                                polygon_prices = self._uses_polygon_prices(
                                    company.info
                                )
                                if polygon_prices:
                                    quarter_result = self.client.get_quarter_report(
                                        company_id,
                                        current_quarter,
                                        company.info.currency,
                                        include_prices=False,
                                    )
                                else:
                                    quarter_result = self.client.get_quarter_report(
                                        company_id,
                                        current_quarter,
                                        company.info.currency,
                                    )
                                current_quarter_reported = quarter_result.quarter
                                if current_quarter == current_quarter_reported:
                                    self.log.error(ErrorMsg.QUARTER_REPORT_FAILED.format(quarter_id=company.info.current_quarter_id, company_id=company_id))
                                    # might be rescheduled
                                    report_date = ReportDate(ticker=company_id, quarter=current_quarter.id, report_date=current_quarter.report_date_this_quarter)
                                    if datetime.now().weekday() == 6:
                                        report_dates.report_dates.append(report_date)
                                    else:
                                        new_report_dates = self.revalidate_report_dates(
                                            ReportDates(report_dates=[report_date])
                                        )
                                        self.service.update_report_date(new_report_dates.report_dates[0])
                                else:
                                    if polygon_prices:
                                        try:
                                            current_quarter_reported = (
                                                self._with_polygon_prices(
                                                    company_id,
                                                    current_quarter_reported,
                                                )
                                            )
                                        except Exception as exception:
                                            self._report_quarter_report_errors(
                                                company_id,
                                                company.info.currency,
                                                current_quarter.id,
                                                [
                                                    "Retrieving the price "
                                                    "range from Polygon "
                                                    f"failed: {exception}"
                                                ],
                                                quarter_result.raw_response,
                                            )
                                            continue
                                    report_errors = self._quarter_structure_errors(
                                        current_quarter,
                                        current_quarter_reported,
                                    )
                                    missing_fields = self._missing_quarter_report_fields(
                                        current_quarter_reported
                                    )
                                    missing_required_fields = sorted(
                                        field
                                        for field in missing_fields
                                        if field
                                        in REQUIRED_REPORTED_QUARTER_FIELDS
                                    )
                                    if missing_required_fields:
                                        report_errors.append(
                                            "Required quarter report fields are "
                                            "missing: "
                                            + ", ".join(
                                                missing_required_fields
                                            )
                                        )
                                    if (
                                        company.info.currency == "$"
                                        and missing_fields
                                    ):
                                        report_errors.append(
                                            "USD quarter report is incomplete: "
                                            + ", ".join(missing_fields)
                                        )
                                    if report_errors:
                                        self._report_quarter_report_errors(
                                            company_id,
                                            company.info.currency,
                                            current_quarter.id,
                                            report_errors,
                                            quarter_result.raw_response,
                                        )
                                        continue
                                    if missing_fields:
                                        self._report_quarter_report_warnings(
                                            company_id,
                                            company.info.currency,
                                            current_quarter.id,
                                            missing_fields,
                                            quarter_result.raw_response,
                                        )
                                    try:
                                        (
                                            current_quarter_reported,
                                            fx_rate,
                                        ) = self._to_currency_override(
                                            company.info,
                                            current_quarter_reported,
                                        )
                                    except Exception as exception:
                                        self._report_quarter_report_errors(
                                            company_id,
                                            company.info.currency,
                                            current_quarter.id,
                                            [
                                                "Converting the quarter to "
                                                f"{company.info.currency_override} "
                                                f"failed: {exception}"
                                            ],
                                            quarter_result.raw_response,
                                        )
                                        continue
                                    current_quarter_reported = scale_to_ads(
                                        current_quarter_reported,
                                        company.info.ads_per_share,
                                    )
                                    self.service.report_quarter(
                                        company_id,
                                        current_quarter_reported,
                                        fx_rate,
                                    )
                                    new_quarter: Quarter = self.compose_new_quarter(current_quarter_reported)
                                    self.service.create_quarter(company_id, new_quarter)
                                    self._notify_quarter_report(
                                        company_id,
                                        current_quarter_reported,
                                        company,
                                        finnhub_companies.get(company_id),
                                    )
                            else:
                                report_dates.report_dates.append(ReportDate(ticker=company_id, quarter=current_quarter.id, report_date=current_quarter.report_date_this_quarter))
                except Exception as exception:
                    self.report_error(
                        exception,
                        operation="process_company",
                        context={"company_id": company_id},
                    )

            if datetime.now().weekday() != 6:
                self._retrieve_price_targets(companies)

            if datetime.now().weekday() == 6:
                new_report_dates = self.revalidate_report_dates(report_dates)

                for report_date, new_report_date in zip(
                    report_dates.report_dates,
                    new_report_dates.report_dates,
                    strict=True,
                ):
                    if report_date.report_date != new_report_date.report_date:
                        self.service.update_report_date(new_report_date)

                self.check_report_dates_next_week(new_report_dates)

        except Exception as exception:
            self.report_error(
                exception,
                operation="run",
            )

    def _to_currency_override(
        self,
        info: Info,
        quarter: Quarter,
    ) -> tuple[Quarter, FxRate | None]:
        # Gemini reports in the company's own currency; a company traded in
        # another one (a foreign ADR) is stored converted. No rate is
        # returned when nothing needed converting.
        target = info.currency_override
        if target is None or target == info.currency:
            return quarter, None
        if self.currency_converter is None:
            raise ValueError("no currency converter is configured")
        return self.currency_converter.convert(
            quarter,
            from_currency=info.currency,
            to_currency=target,
        )

    @staticmethod
    def _needs_initialization(company: Company | None) -> bool:
        if company is None:
            return True
        return company.info.current_quarter_id not in company.quarters

    def onboard_company(self, ticker: str) -> Company | None:
        company = self._initialize_company(ticker)
        if company is not None:
            self._retrieve_price_targets(
                {ticker: company},
                lookback_days=PRICE_TARGET_ONBOARDING_LOOKBACK_DAYS,
            )
        return company

    def _initialize_company(self, company_id: str) -> Company | None:
        sec = self._load_sec_facts(company_id)
        if sec is not None and sec.quarterly:
            # SEC filings supply the older quarters' financials, so Gemini
            # is only asked for the latest reported quarter.
            result = self.client.get_initial_stock_data(
                company_id,
                financials_from_sec=True,
            )
        else:
            result = self.client.get_initial_stock_data(company_id)
        company, enrichment_notes = self._enrich_initial_company(
            company_id,
            result.company,
            sec,
        )
        currency = company.info.currency
        missing_fields = self._missing_reported_quarter_fields(company)
        # The latest reported quarter must have its core figures; older
        # ones may be missing (a foreign company's history, for one).
        latest_quarter_id = self._quarter_id_offset(
            company.info.current_quarter_id,
            1,
        )
        missing_required_fields = sorted(
            field
            for field in missing_fields
            if field.startswith(f"{latest_quarter_id}.")
            and field.rsplit(".", maxsplit=1)[-1]
            in REQUIRED_REPORTED_QUARTER_FIELDS
        )
        retrieval_errors = list(result.errors) + enrichment_notes

        if missing_required_fields:
            retrieval_errors.append(
                "Required reported-quarter fields are missing: "
                + ", ".join(missing_required_fields)
            )
            self._report_initial_company_errors(
                company_id,
                currency,
                retrieval_errors,
                result.raw_response,
            )
            return None

        if retrieval_errors or missing_fields:
            if missing_fields:
                retrieval_errors.append(
                    "Accepted unavailable reported-quarter fields: "
                    + ", ".join(missing_fields)
                )
            self._report_initial_company_warnings(
                company_id,
                currency,
                retrieval_errors,
                result.raw_response,
            )

        self.service.init_company(id=company_id, data=company)
        return company

    def _uses_polygon_prices(self, info: Info) -> bool:
        return self.price_ranges is not None and prices_come_from_polygon(info)

    def _with_polygon_prices(self, company_id: str, quarter: Quarter) -> Quarter:
        if quarter.report_date_this_quarter is None:
            raise ValueError(f"{quarter.id} has no report date")
        ranges = self.price_ranges.ranges(
            company_id,
            {
                quarter.id: (
                    quarter.report_date_previous_quarter,
                    quarter.report_date_this_quarter,
                ),
            },
        )
        price_range = ranges[quarter.id]
        return quarter.model_copy(update={
            "price_min": None if price_range is None else price_range.low,
            "price_max": None if price_range is None else price_range.high,
        })

    def _load_sec_facts(self, company_id: str) -> SecFacts | None:
        if self.sec_client is None:
            return None
        try:
            cik = self.sec_client.get_cik(company_id)
            if cik is None:
                return None
            facts = self.sec_client.get_company_facts(cik)
            since = date.today() - timedelta(
                days=SEC_QUARTERLY_FILING_MAX_AGE_DAYS
            )
            return SecFacts(
                cik=cik,
                facts=facts,
                quarterly=sec_financials.has_quarterly_filings(
                    facts,
                    since=since,
                ),
            )
        except Exception as exception:
            self.errors.report_warning_message(
                f"SEC lookup failed for {company_id}, so Gemini supplies "
                f"every quarter: {exception}",
                logger=self.log,
                source=self.name,
                operation="initialize_company_sec",
                context={"company_id": company_id},
            )
            return None

    def _enrich_initial_company(
        self,
        company_id: str,
        company: Company,
        sec: SecFacts | None,
    ) -> tuple[Company, list[str]]:
        # Gemini supplied the structure and the latest reported quarter.
        # What the other sources can provide more reliably replaces what
        # Gemini returned: older quarters' financials from SEC filings, and
        # price ranges from Polygon. Failures are notes, never fatal.
        notes: list[str] = []
        info_updates: dict[str, object] = {}
        quarters = dict(company.quarters)
        current_id = company.info.current_quarter_id
        latest_id = self._quarter_id_offset(current_id, 1)
        reported = {
            quarter_id: quarter
            for quarter_id, quarter in quarters.items()
            if quarter_id != current_id
        }

        if sec is not None:
            info_updates["cik"] = sec.cik
            if sec.quarterly and company.info.currency == "$":
                try:
                    extracted = sec_financials.extract_quarters(
                        sec.facts,
                        {
                            quarter_id: quarter.ending_month
                            for quarter_id, quarter in reported.items()
                        },
                    )
                    estimates: dict[str, list[str]] = {}
                    for quarter_id, found in extracted.items():
                        values = dict(found.values)
                        if quarter_id == latest_id:
                            # Gemini's figures for the latest quarter stand;
                            # SEC only fills what Gemini left empty.
                            values = {
                                name: value
                                for name, value in values.items()
                                if getattr(quarters[quarter_id], name) is None
                            }
                        if values:
                            quarters[quarter_id] = quarters[
                                quarter_id
                            ].model_copy(update=values)
                        marks = sorted(found.estimated & set(values))
                        if marks:
                            estimates[quarter_id] = marks
                    if estimates:
                        info_updates["estimates"] = estimates
                except Exception as exception:
                    self.log.exception(
                        "SEC financials for %s failed", company_id
                    )
                    notes.append(
                        f"Financials from SEC filings failed: {exception}"
                    )
            elif sec.quarterly:
                notes.append(
                    "SEC filings were not used: the reporting currency is "
                    f"{company.info.currency}, not $"
                )

        if self._uses_polygon_prices(company.info):
            try:
                ranges = self.price_ranges.ranges(
                    company_id,
                    {
                        quarter_id: (
                            quarter.report_date_previous_quarter,
                            quarter.report_date_this_quarter,
                        )
                        for quarter_id, quarter in reported.items()
                        if quarter.report_date_this_quarter is not None
                    },
                )
                for quarter_id, price_range in ranges.items():
                    quarters[quarter_id] = quarters[quarter_id].model_copy(
                        update={
                            "price_min": (
                                None if price_range is None else price_range.low
                            ),
                            "price_max": (
                                None if price_range is None else price_range.high
                            ),
                        },
                    )
            except Exception as exception:
                self.log.exception("Polygon prices for %s failed", company_id)
                notes.append(f"Price ranges from Polygon failed: {exception}")

        if self.ads_detector is not None:
            try:
                ratio = self.ads_detector.detect(company_id)
            except Exception as exception:
                self.log.exception("ADS ratio for %s failed", company_id)
                notes.append(f"Detecting the ADS ratio failed: {exception}")
                ratio = None
            if ratio is not None:
                # Gemini's per-share figures are per ordinary share.
                info_updates["ads_per_share"] = ratio.ads_per_share
                info_updates["ads_checked_on"] = date.today()
                quarters = {
                    quarter_id: scale_to_ads(quarter, ratio.ads_per_share)
                    for quarter_id, quarter in quarters.items()
                }

        enriched = company.model_copy(update={
            "info": company.info.model_copy(update=info_updates),
            "quarters": quarters,
        })
        return enriched, notes

    @staticmethod
    def _missing_reported_quarter_fields(company: Company) -> list[str]:
        current_quarter_id = company.info.current_quarter_id
        return sorted(
            f"{quarter.id}.{field}"
            for quarter in company.quarters.values()
            if quarter.id != current_quarter_id
            for field in StockDataRetrieverRunner._missing_quarter_data_fields(
                quarter
            )
        )

    @staticmethod
    def _missing_quarter_data_fields(quarter: Quarter) -> list[str]:
        return sorted(
            field
            for field in REPORTED_QUARTER_DATA_FIELDS
            if getattr(quarter, field) is None
        )

    @staticmethod
    def _missing_quarter_report_fields(quarter: Quarter) -> list[str]:
        return [
            field
            for field in StockDataRetrieverRunner._missing_quarter_data_fields(
                quarter
            )
            if field not in OPTIONAL_QUARTER_REPORT_FIELDS
        ]

    @staticmethod
    def _quarter_structure_errors(
        expected: Quarter,
        actual: Quarter,
    ) -> list[str]:
        errors = []
        for field in (
            "id",
            "name",
            "ending_month",
            "report_date_previous_quarter",
            "report_date_this_quarter",
        ):
            expected_value = getattr(expected, field)
            actual_value = getattr(actual, field)
            if actual_value is None:
                errors.append(f"Required quarter field {field} is missing")
            elif actual_value != expected_value:
                errors.append(
                    f"Quarter field {field} changed from "
                    f"{expected_value!r} to {actual_value!r}"
                )
        return errors

    def _report_initial_company_errors(
        self,
        company_id: str,
        currency: str,
        errors: list[str],
        raw_response: str,
    ) -> None:
        message = "Gemini initialization failed:\n" + "\n".join(
            f"{index}. {error}"
            for index, error in enumerate(errors, start=1)
        )
        message += (
            "\n\n================ GEMINI RESPONSE START ================\n"
            f"{raw_response}\n"
            "================= GEMINI RESPONSE END ================="
        )
        self.errors.report_error_message(
            message,
            logger=self.log,
            source=self.name,
            operation="initialize_company",
            context={
                "company_id": company_id,
                "currency": currency,
                "error_count": len(errors),
            },
        )

    def _report_initial_company_warnings(
        self,
        company_id: str,
        currency: str,
        errors: list[str],
        raw_response: str,
    ) -> None:
        message = (
            "Gemini initialization completed with accepted "
            "retrieval errors:\n"
            + "\n".join(
                f"{index}. {error}"
                for index, error in enumerate(errors, start=1)
            )
            + "\n\n================ GEMINI RESPONSE START ================\n"
            + raw_response
            + "\n================= GEMINI RESPONSE END ================="
        )
        self.errors.report_warning_message(
            message,
            logger=self.log,
            source=self.name,
            operation="initialize_company",
            context={
                "company_id": company_id,
                "currency": currency,
                "error_count": len(errors),
            },
        )

    def _report_quarter_report_errors(
        self,
        company_id: str,
        currency: str,
        quarter_id: str,
        errors: list[str],
        raw_response: str,
    ) -> None:
        message = "Gemini quarter report rejected:\n" + "\n".join(
            f"{index}. {error}"
            for index, error in enumerate(errors, start=1)
        )
        message += (
            "\n\n================ GEMINI RESPONSE START ================\n"
            f"{raw_response}\n"
            "================= GEMINI RESPONSE END ================="
        )
        self.errors.report_error_message(
            message,
            logger=self.log,
            source=self.name,
            operation="retrieve_quarter_report",
            context={
                "company_id": company_id,
                "currency": currency,
                "quarter_id": quarter_id,
                "error_count": len(errors),
            },
        )

    def _report_quarter_report_warnings(
        self,
        company_id: str,
        currency: str,
        quarter_id: str,
        missing_fields: list[str],
        raw_response: str,
    ) -> None:
        message = (
            "Gemini quarter report completed with accepted non-USD missing "
            "fields:\n"
            + "\n".join(
                f"{index}. {field}"
                for index, field in enumerate(missing_fields, start=1)
            )
            + "\n\n================ GEMINI RESPONSE START ================\n"
            + raw_response
            + "\n================= GEMINI RESPONSE END ================="
        )
        self.errors.report_warning_message(
            message,
            logger=self.log,
            source=self.name,
            operation="retrieve_quarter_report",
            context={
                "company_id": company_id,
                "currency": currency,
                "quarter_id": quarter_id,
                "missing_field_count": len(missing_fields),
            },
        )

    def report_error(
        self,
        exception: BaseException,
        *,
        operation: str,
        context: Mapping[str, object] | None = None,
    ) -> str:
        return self.errors.report(
            exception,
            logger=self.log,
            source=self.name,
            operation=operation,
            context=context,
        )

    def _retrieve_price_targets(
        self,
        companies: dict[str, Company | None],
        *,
        lookback_days: int = PRICE_TARGET_LOOKBACK_DAYS,
    ) -> None:
        tickers = sorted(
            company_id
            for company_id, company in companies.items()
            if company is not None
        )
        today = datetime.now().date()
        start_date = today - timedelta(days=lookback_days)
        end_date = today
        institutions = InstitutionRegistry(
            self.service.get_institutions()
        )
        candidates = self.client.get_price_targets(
            tickers,
            start_date,
            end_date,
        )

        resolved_targets: list[tuple[Target, InstitutionRecord]] = []
        for candidate in candidates.targets:
            institution = institutions.resolve_or_create(
                candidate.institution
            )
            resolved_targets.append((
                self._to_target(candidate, institution),
                institution,
            ))

        resolved_targets = self._reconcile_new_institutions(
            resolved_targets,
            institutions,
        )
        if institutions.new_institutions:
            self.service.create_institutions(institutions.new_institutions)
            self._notify_new_institutions(
                list(institutions.new_institutions.values())
            )
        latest_target_dates = self._latest_price_target_dates(
            companies,
            institutions,
        )
        quarter_start_dates = self._quarter_start_dates(companies)

        notifiable_targets: dict[
            str, list[tuple[Target, InstitutionRecord]]
        ] = {}
        for target, institution in sorted(
            resolved_targets,
            key=lambda resolved: resolved[0].date,
            reverse=True,
        ):
            if not institution.enabled:
                continue

            target_key = self._price_target_key(
                target,
                institutions,
            )
            ticker_target_dates = latest_target_dates.setdefault(
                target.ticker,
                {},
            )
            latest_target_date = ticker_target_dates.get(target_key)
            quarter_start = quarter_start_dates.get(target.ticker)
            if (
                latest_target_date is not None
                and quarter_start is not None
                and latest_target_date > quarter_start
                and target.date > quarter_start
            ):
                continue

            if institution.trusted:
                # Enrichment failing (a bad Gemini response, a network
                # error) must not cost us the target itself - fall back to
                # persisting it unenriched rather than losing it entirely.
                enriched_target = self._enrich_price_target(target)
                if enriched_target is not None:
                    target = enriched_target

            if self._persist_price_target(target):
                ticker_target_dates[target_key] = target.date
                notifiable_targets.setdefault(target.ticker, []).append(
                    (target, institution)
                )

        closes = self._latest_closes(list(notifiable_targets), today)
        for ticker, ticker_targets in notifiable_targets.items():
            self._notify_price_targets(
                ticker,
                ticker_targets,
                close=closes.get(ticker),
            )

        self.log.info(
            LogMsg.TARGETS_RETRIEVED.format(
                target_count=len(candidates.targets),
                company_count=len(tickers),
                start_date=start_date,
                end_date=end_date,
            )
        )

    @staticmethod
    def _to_target(
        candidate: TargetCandidate,
        institution: InstitutionRecord,
    ) -> Target:
        return Target(
            ticker=candidate.ticker,
            institution=institution.name,
            date=candidate.date,
            price=candidate.price,
            rating=candidate.rating,
            source=candidate.source,
        )

    def _reconcile_new_institutions(
        self,
        resolved_targets: list[tuple[Target, InstitutionRecord]],
        institutions: InstitutionRegistry,
    ) -> list[tuple[Target, InstitutionRecord]]:
        if not institutions.new_institutions:
            return resolved_targets

        new_names = [
            institution.name
            for institution in institutions.new_institutions.values()
        ]
        existing_names = [
            institution.name
            for candidate_key, institution in institutions.institutions.items()
            if candidate_key not in institutions.new_institutions
        ]

        try:
            resolutions = self.client.resolve_new_institutions(
                new_names,
                existing_names,
            )
        except Exception as exception:
            self.report_error(
                exception,
                operation="resolve_new_institutions",
                context={"institutions": ", ".join(new_names)},
            )
            return resolved_targets

        resolutions_by_name = {
            normalize_institution_name(resolution.institution): resolution
            for resolution in resolutions.resolutions
        }

        redirects: dict[str, InstitutionRecord] = {}
        for candidate_key, candidate in list(
            institutions.new_institutions.items()
        ):
            resolution = resolutions_by_name.get(
                normalize_institution_name(candidate.name)
            )
            if resolution is None:
                continue

            if (
                not resolution.is_alias
                and resolution.institutional_weight is not None
                and resolution.media_shock_value is not None
            ):
                institutions.new_institutions[candidate_key] = (
                    candidate.model_copy(update={
                        "rating": InstitutionRating(
                            institutional_weight=(
                                resolution.institutional_weight
                            ),
                            media_shock_value=resolution.media_shock_value,
                        ),
                    })
                )
                continue

            canonical = institutions.resolve(resolution.alias_of or "")
            if canonical is None:
                continue

            del institutions.new_institutions[candidate_key]
            self.service.add_institution_alias(
                institution_id=institutions.canonical_key(canonical.name),
                alias_key=candidate_key,
                alias_name=candidate.name,
            )
            redirects[candidate_key] = canonical

        if not redirects:
            return resolved_targets

        reconciled: list[tuple[Target, InstitutionRecord]] = []
        for target, institution in resolved_targets:
            canonical = redirects.get(
                normalize_institution_name(institution.name)
            )
            if canonical is None:
                reconciled.append((target, institution))
            else:
                reconciled.append((
                    target.model_copy(
                        update={"institution": canonical.name}
                    ),
                    canonical,
                ))
        return reconciled

    def _notify_new_institutions(
        self,
        institutions: list[InstitutionRecord],
    ) -> None:
        try:
            self.discord.post_eventlog(
                discord_templates.new_institutions(institutions)
            )
        except Exception as exception:
            self.report_error(
                exception,
                operation="notify_new_institutions",
                context={
                    "institutions": ", ".join(
                        institution.name for institution in institutions
                    ),
                },
            )

    def _enrich_price_target(self, target: Target) -> Target | None:
        try:
            return self.client.get_target_report(target)
        except Exception as exception:
            self.report_error(
                exception,
                operation="retrieve_price_target_report",
                context=self._price_target_context(target),
            )
            return None

    def _persist_price_target(self, target: Target) -> bool:
        try:
            stored_target = CompanyTarget.model_validate(
                target.model_dump(exclude={"ticker"})
            )
            self.service.upsert_target(target.ticker, stored_target)
        except Exception as exception:
            self.report_error(
                exception,
                operation="persist_price_target",
                context=self._price_target_context(target),
            )
            return False
        return True

    def _notify_price_targets(
        self,
        ticker: str,
        entries: list[tuple[Target, InstitutionRecord]],
        close: Decimal | None = None,
    ) -> None:
        # One list post for everything retrieved for the ticker, trusted
        # and untrusted institutions alike, with every target's details in
        # a thread under it in the same order.
        targets = [target for target, _institution in entries]
        context = {"ticker": ticker, "count": str(len(targets))}
        try:
            posted = self.discord.post_with_thread_if_channel_exists(
                ticker,
                discord_templates.ticker_price_targets(targets, close=close),
                thread_name=discord_templates.price_targets_thread_name(
                    ticker,
                    targets,
                ),
                thread_payloads=discord_templates.price_targets_thread_payloads(
                    [
                        (target, institution.rating)
                        for target, institution in entries
                    ],
                    close=close,
                ),
            )
            if posted is not None:
                self._warn_if_thread_undelivered(
                    posted,
                    f"the {len(targets)} price target(s) for {ticker}",
                    context,
                )
                return
            self._post_price_targets_to_eventlog(ticker, entries, close)
        except Exception as exception:
            self.report_error(
                exception,
                operation="notify_price_targets",
                context=context,
            )

    def _post_price_targets_to_eventlog(
        self,
        ticker: str,
        entries: list[tuple[Target, InstitutionRecord]],
        close: Decimal | None,
    ) -> None:
        # No ticker channel: the webhook cannot carry a thread, so
        # researched targets go inline one by one (they hold the report)
        # and the rest as a list, or alone when there is just one.
        researched = [e for e in entries if e[0].report is not None]
        plain = [e for e in entries if e[0].report is None]
        for target, institution in researched:
            self.discord.post_eventlog(
                discord_templates.price_target(
                    target,
                    rating=institution.rating,
                    close=close,
                ),
            )
        if len(plain) == 1:
            target, institution = plain[0]
            self.discord.post_eventlog(
                discord_templates.price_target(
                    target,
                    rating=institution.rating,
                    close=close,
                ),
            )
        elif plain:
            self.discord.post_eventlog(
                discord_templates.price_targets(
                    ticker,
                    [target for target, _institution in plain],
                    close=close,
                ),
            )

    def _warn_if_thread_undelivered(
        self,
        posted: ThreadedPost,
        subject: str,
        context: dict[str, str],
    ) -> None:
        if posted.thread_error is None:
            return
        self.errors.report_warning_message(
            f"The details of {subject} were not delivered as a thread: "
            f"{posted.thread_error}. Post: {posted.url}",
            logger=self.log,
            source=self.name,
            operation="notify_price_target_thread",
            context=context,
        )

    def _latest_closes(
        self,
        tickers: list[str],
        today: date,
    ) -> dict[str, Decimal]:
        # A price is shown only if today's retrieval produced it: a stale one
        # (the price job failed or hasn't run yet) is left out rather than
        # presented as current, and no failure here may block the post.
        if not tickers:
            return {}
        try:
            prices = self.indicators_service.get_prices(tickers)
        except Exception as exception:
            self.report_error(
                exception,
                operation="retrieve_latest_closes",
                context={"count": str(len(tickers))},
            )
            return {}
        return {
            ticker: price.value
            for ticker, price in prices.items()
            if price.retrieved_on == today
        }

    @staticmethod
    def _price_target_context(target: Target) -> dict[str, str]:
        return {
            "ticker": target.ticker,
            "institution": target.institution,
            "date": target.date.isoformat(),
        }

    @classmethod
    def _latest_price_target_dates(
        cls,
        companies: dict[str, Company | None],
        institutions: InstitutionRegistry,
    ) -> dict[str, dict[tuple[str, Decimal], date]]:
        latest_dates = {}
        for ticker, company in companies.items():
            if company is None:
                continue

            ticker_dates = {}
            for target in company.targets.values():
                target_key = cls._price_target_key(
                    target,
                    institutions,
                )
                latest_date = ticker_dates.get(target_key)
                if latest_date is None or target.date > latest_date:
                    ticker_dates[target_key] = target.date
            latest_dates[ticker] = ticker_dates
        return latest_dates

    @staticmethod
    def _quarter_start_dates(
        companies: dict[str, Company | None],
    ) -> dict[str, date]:
        # The current fiscal quarter starts the day after the previous
        # quarter's report date; that date itself still belongs to the
        # previous quarter.
        quarter_starts = {}
        for ticker, company in companies.items():
            if company is None:
                continue
            current_quarter = company.quarters.get(
                company.info.current_quarter_id
            )
            if current_quarter is None:
                continue
            quarter_starts[ticker] = (
                current_quarter.report_date_previous_quarter
            )
        return quarter_starts

    @staticmethod
    def _price_target_key(
        target: CompanyTarget,
        institutions: InstitutionRegistry,
    ) -> tuple[str, Decimal]:
        return (
            institutions.canonical_key(target.institution),
            target.price,
        )

    def revalidate_report_dates(self, report_dates: ReportDates) -> ReportDates:
        new_report_dates = self.client.revalidate_report_dates(report_dates)
        requested_ids = [
            (report.ticker, report.quarter)
            for report in report_dates.report_dates
        ]
        returned_ids = [
            (report.ticker, report.quarter)
            for report in new_report_dates.report_dates
        ]
        if returned_ids != requested_ids:
            raise ValueError(
                "Gemini changed report-date identities: "
                f"expected {requested_ids}, received {returned_ids}"
            )
        for requested, returned in zip(
            report_dates.report_dates,
            new_report_dates.report_dates,
            strict=True,
        ):
            if requested.report_date is not None and returned.report_date is None:
                raise ValueError(
                    "Gemini removed a known report date for "
                    f"{requested.ticker} {requested.quarter}"
                )
        return new_report_dates

    def _notify_quarter_report(
        self,
        ticker: str,
        quarter: Quarter,
        company: Company,
        finnhub_company: FinnhubCompany | None,
    ) -> None:
        qoq_quarter = company.quarters.get(
            self._quarter_id_offset(quarter.id, 1)
        )
        yoy_quarter = company.quarters.get(
            self._quarter_id_offset(quarter.id, 4)
        )
        estimates = self._quarter_estimates(finnhub_company, quarter.id)

        earnings_report = discord_templates.quarter_report(
            quarter,
            ticker,
            qoq_quarter=qoq_quarter,
            yoy_quarter=yoy_quarter,
            estimates=estimates,
        )
        message_url = self.discord.post_if_channel_exists(
            ticker,
            discord_templates.ticker_quarter_report(
                quarter,
                qoq_quarter=qoq_quarter,
                yoy_quarter=yoy_quarter,
                estimates=estimates,
            ),
        )
        if not message_url:
            self.discord.post_earnings(earnings_report)
            return

        self.discord.post_earnings(
            discord_templates.quarter_report_link(ticker, message_url)
        )

    @staticmethod
    def _quarter_id_offset(quarter_id: str, quarters_back: int) -> str:
        year = int(quarter_id[:2])
        quarter = int(quarter_id[3])
        total = quarter - 1 - quarters_back
        year += total // 4
        quarter = total % 4 + 1
        return f"{year % 100:02d}Q{quarter}"

    @staticmethod
    def _quarter_estimates(
        finnhub_company: FinnhubCompany | None,
        quarter_id: str,
    ) -> FinnhubEarnings | None:
        if finnhub_company is None:
            return None
        finnhub_quarter = finnhub_company.root.get(quarter_id)
        if finnhub_quarter is None:
            return None
        snapshots = finnhub_quarter.root
        if not snapshots:
            return None
        return snapshots[max(snapshots)]

    def compose_new_quarter(self, previous_quarter: Quarter) -> Quarter:
        previous_id_year = int(previous_quarter.id[:2])
        previous_q = int(previous_quarter.id[3])
        previous_month_year = int(previous_quarter.ending_month[:2])
        previous_m = int(previous_quarter.ending_month[3:])

        if previous_q == 4:
            next_q = 1
            next_id_year = previous_id_year + 1
        else:
            next_q = previous_q + 1
            next_id_year = previous_id_year

        next_m = previous_m + 3
        next_month_year = previous_month_year
        if next_m > 12:
            next_m -= 12
            next_month_year += 1

        id_yy_str = f"{next_id_year:02d}"
        ending_month = f"{next_month_year:02d}-{next_m:02d}"

        return Quarter(name=f"Q{next_q} 20{id_yy_str}", ending_month=ending_month, id=f"{id_yy_str}Q{next_q}", report_date_previous_quarter=previous_quarter.report_date_this_quarter)

    def check_report_dates_next_week(self, report_dates: ReportDates):
        today = datetime.now().date()
        if today.weekday() != 6:
            self.log.error(ErrorMsg.SHOULD_RUN_ON_SUNDAY.format(today=calendar.day_name[today.weekday()]))
            return

        report_map = {}
        for i in range(1, 6):
            weekday_date = today + timedelta(days=i)
            report_map[weekday_date] = []

        for report in report_dates.report_dates:
            if report.report_date in report_map:
                info = f"{report.ticker} - {report.quarter}"
                report_map[report.report_date].append(info)

        fields = list()
        for day in sorted(report_map.keys()):
            fields.append({
                "name": f"**{day.strftime('%A')}** ({day.isoformat()})",
                "value": "\n".join(report_map[day]),
                "inline": False,
            })

        self.discord.post_earnings(discord_templates.upcoming_earnings(fields))
