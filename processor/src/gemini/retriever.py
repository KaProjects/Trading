import calendar
import logging
from collections.abc import Mapping
from datetime import date, datetime, timedelta
from decimal import Decimal

import utils
from discord.client import DiscordClient
from error_reporting import ErrorReporter
from gemini import discord_templates
from gemini.client import GeminiClient
from gemini.institutions import InstitutionRegistry
from gemini.models import (
    Company,
    CompanyTarget,
    InstitutionRecord,
    Quarter,
    ReportDate,
    ReportDates,
    Target,
)
from gemini.service import FirebaseService
from gemini.strings import ErrorMsg, LogMsg

RUNNER_NAME = "StockDataRetriever"
PRICE_TARGET_DUPLICATE_WINDOW_DAYS = 7
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
OPTIONAL_QUARTER_REPORT_FIELDS = frozenset({
    "reported_capex",
    "reported_free_cash_flow",
})
logger = logging.getLogger(RUNNER_NAME)


class StockDataRetrieverRunner:
    log = logger
    name = RUNNER_NAME
    # model = "gemini-3-flash-preview"
    model = "gemini-3.1-pro-preview"

    def __init__(
        self,
        gemini_api_key: str | None = None,
        client: GeminiClient | None = None,
        service: FirebaseService | None = None,
        discord: DiscordClient | None = None,
        error_reporter: ErrorReporter | None = None,
    ) -> None:
        if client is None:
            if gemini_api_key is None:
                raise ValueError("gemini_api_key is required without a client")
            client = GeminiClient(api_key=gemini_api_key, model=self.model)
        if discord is None:
            raise ValueError("discord is required")

        self.errors = error_reporter or ErrorReporter(environment="local")
        self.client = client
        self.service = (
            service
            if service is not None
            else FirebaseService(error_reporter=self.errors)
        )
        self.discord = discord

    def run(self):
        try:
            companies: dict = self.service.get_companies()
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
                                    self.service.report_quarter(company_id, current_quarter_reported)
                                    new_quarter: Quarter = self.compose_new_quarter(current_quarter_reported)
                                    self.service.create_quarter(company_id, new_quarter)
                                    self._notify_quarter_report(
                                        company_id,
                                        current_quarter_reported,
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

    @staticmethod
    def _needs_initialization(company: Company | None) -> bool:
        if company is None:
            return True
        return company.info.current_quarter_id not in company.quarters

    def _initialize_company(self, company_id: str) -> Company | None:
        result = self.client.get_initial_stock_data(company_id)
        company = result.company
        currency = company.info.currency
        missing_fields = self._missing_reported_quarter_fields(company)
        missing_required_fields = sorted(
            field
            for field in missing_fields
            if field.rsplit(".", maxsplit=1)[-1]
            in REQUIRED_REPORTED_QUARTER_FIELDS
        )
        retrieval_errors = list(result.errors)

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
            "Gemini initialization completed with accepted non-USD "
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
    ) -> None:
        tickers = sorted(
            company_id
            for company_id, company in companies.items()
            if company is not None
        )
        today = datetime.now().date()
        start_date = today - timedelta(days=2)
        end_date = today
        institutions = InstitutionRegistry(
            self.service.get_institutions()
        )
        targets = self.client.get_price_targets(
            tickers,
            start_date,
            end_date,
        )

        resolved_targets: list[tuple[Target, InstitutionRecord]] = []
        for target in targets.targets:
            institution = institutions.resolve_or_create(
                target.institution
            )
            resolved_targets.append((
                target.model_copy(
                    update={"institution": institution.name}
                ),
                institution,
            ))

        if institutions.new_institutions:
            self.service.create_institutions(
                institutions.new_institutions
            )
        latest_target_dates = self._latest_price_target_dates(
            companies,
            institutions,
        )

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
            if (
                latest_target_date is not None
                and (target.date - latest_target_date).days
                <= PRICE_TARGET_DUPLICATE_WINDOW_DAYS
            ):
                continue

            if institution.trusted:
                target = self._enrich_price_target(target)
                if target is None:
                    continue

            if self._persist_price_target(target):
                ticker_target_dates[target_key] = target.date
                self._notify_price_target(target)

        self.log.info(
            LogMsg.TARGETS_RETRIEVED.format(
                target_count=len(targets.targets),
                company_count=len(tickers),
                start_date=start_date,
                end_date=end_date,
            )
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

    def _notify_price_target(self, target: Target) -> None:
        try:
            if self.discord.post_if_channel_exists(
                target.ticker,
                discord_templates.ticker_price_target(target),
            ):
                return
            self.discord.post_eventlog(
                discord_templates.price_target(target),
            )
        except Exception as exception:
            self.report_error(
                exception,
                operation="notify_price_target",
                context=self._price_target_context(target),
            )

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
    ) -> None:
        earnings_report = discord_templates.quarter_report(
            quarter,
            ticker,
        )
        message_url = self.discord.post_if_channel_exists(
            ticker,
            discord_templates.ticker_quarter_report(quarter),
        )
        if not message_url:
            self.discord.post_earnings(earnings_report)
            return

        self.discord.post_earnings(
            discord_templates.quarter_report_link(ticker, message_url)
        )

    def compose_new_quarter(self, previous_quarter: Quarter) -> Quarter:
        previous_y = int(previous_quarter.id[:2])
        previous_q = int(previous_quarter.id[3])
        previous_m = int(previous_quarter.ending_month[3:])

        if previous_q == 4:
            next_q = 1
            next_y = previous_y + 1
        else:
            next_q = previous_q + 1
            next_y = previous_y

        next_m = previous_m + 3
        if next_m > 12:
            next_m -= 12

        yy_str = f"{next_y:02d}"
        mm_str = f"{next_m:02d}"

        return Quarter(name=f"Q{next_q} 20{yy_str}", ending_month=f"{yy_str}-{mm_str}", id=f"{yy_str}Q{next_q}", report_date_previous_quarter=previous_quarter.report_date_this_quarter)

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
