import calendar
import logging
from collections.abc import Mapping
from datetime import date, datetime, timedelta
from decimal import Decimal

import utils
from discord.client import DiscordClient
from error_reporting import ErrorReporter
from gemini.client import GeminiClient
from gemini.institutions import InstitutionRegistry
from gemini.models import (
    Company,
    CompanyTarget,
    Quarter,
    ReportDate,
    ReportDates,
    Target,
)
from gemini.service import FirebaseService
from gemini.strings import ErrorMsg, LogMsg

RUNNER_NAME = "StockDataRetriever"
PRICE_TARGET_DUPLICATE_WINDOW_DAYS = 7
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
                    if companies.get(company_id) is None:
                        company: Company = self.client.get_initial_stock_data(company_id)
                        self.service.init_company(id=company_id, data=company)
                        companies[company_id] = company
                    else:
                        company = companies.get(company_id)
                        current_quarter: Quarter = company.quarters.get(company.info.current_quarter_id)
                        if current_quarter is None:
                            self.log.error(ErrorMsg.QUARTER_NOT_FOUND.format(quarter_id=company.info.current_quarter_id, company_id=company_id))
                        else:
                            if utils.is_past_date(date=current_quarter.report_date_this_quarter):
                                current_quarter_reported: Quarter = self.client.get_quarter_report(company_id, current_quarter)
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
                                    self.service.report_quarter(company_id, current_quarter_reported)
                                    new_quarter: Quarter = self.compose_new_quarter(current_quarter_reported)
                                    self.service.create_quarter(company_id, new_quarter)
                                    self.discord.post_earnings(
                                        self.format_quarter_for_discord(
                                            quarter=current_quarter_reported,
                                            ticker=company_id,
                                        )
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

        resolved_targets: list[tuple[Target, bool]] = []
        for target in targets.targets:
            institution = institutions.resolve_or_create(
                target.institution
            )
            resolved_targets.append((
                target.model_copy(
                    update={"institution": institution.name}
                ),
                institution.enabled,
            ))

        if institutions.new_institutions:
            self.service.create_institutions(
                institutions.new_institutions
            )
        latest_target_dates = self._latest_price_target_dates(
            companies,
            institutions,
        )

        for target, institution_enabled in sorted(
            resolved_targets,
            key=lambda resolved: resolved[0].date,
            reverse=True,
        ):
            if not institution_enabled:
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
                self.format_target_for_ticker_discord(target),
            ):
                return
            self.discord.post_eventlog(
                self.format_target_for_discord(target),
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

    def format_quarter_for_discord(
        self,
        quarter: Quarter,
        ticker: str,
    ) -> dict[str, object]:
        return {
            "username": "Quarterly Results Reporter",
            "avatar_url": (
                "https://cdn-icons-png.flaticon.com/512/1390/1390704.png"
            ),
            "embeds": [{
                "title": f"{ticker} - {quarter.name} report",
                "description": (
                    f"ending: {quarter.ending_month} | "
                    f"reported: {quarter.report_date_this_quarter}"
                ),
                "color": 3066993,
                "fields": [
                    {
                        "name": "Financials",
                        "value": (
                            f"**Revenues:** {self.format_financial(quarter.reported_revenues)}\n"
                            f"**Gross Profit:** {self.format_financial(quarter.reported_gross_profit)}\n"
                            f"**Oper. Income:** {self.format_financial(quarter.reported_operating_income)}\n"
                            f"**Net Income:** {self.format_financial(quarter.reported_net_income)}\n"
                            f"**Divs:** {self.format_financial(quarter.reported_div)}\n"
                            f"**Shares:** {self.format_financial(quarter.reported_shares)}\n"
                            f"**EPS:** {quarter.reported_eps}"
                        ),
                        "inline": False
                    },
                    {
                        "name": "Price Range (from previous report)",
                        "value": (
                            f"Low: **${quarter.price_min}** — "
                            f"High: **${quarter.price_max}**"
                        ),
                        "inline": False
                    }
                ]
            }]
        }

    def format_target_for_discord(self, target: Target) -> dict[str, object]:
        return {
            "username": "Institutional Price Target Reporter",
            "avatar_url": (
                "https://cdn-icons-png.flaticon.com/512/1872/1872505.png"
            ),
            "embeds": [{
                "title": (
                    f"🎯 {target.ticker} | ${target.price} | "
                    f"{target.date.isoformat()}"
                ),
                "color": 0xF1C40F,
                "fields": [
                    {
                        "name": target.institution,
                        "value": target.rating or "Not provided",
                        "inline": False,
                    },
                    {
                        "name": "Source",
                        "value": target.source,
                        "inline": False,
                    },
                ],
            }],
        }

    @staticmethod
    def format_target_for_ticker_discord(
        target: Target,
    ) -> dict[str, object]:
        return {
            "embeds": [{
                "title": f"🎯 new price target ${target.price}",
                "color": 0xF1C40F,
                "fields": [{
                    "name": target.institution,
                    "value": (
                        f"{target.rating or 'Not provided'}\n"
                        f"{target.date.isoformat()}\n"
                        f"source: {target.source}"
                    ),
                    "inline": False,
                }],
            }],
        }

    def format_financial(self, original: str):
        if original is None: return ""
        try:
            result = float(original)
            if result == 0: return "-"
            if abs(result) >= 1000:
                return str(round(result / 1000, 2)) + "B"
            else:
                return str(round(result, 2)) + "M"
        except (ValueError, TypeError):
            return ""

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

        self.discord.post_earnings(
            {
                "username": "Quarterly Results Reporter",
                "avatar_url": (
                    "https://cdn-icons-png.flaticon.com/512/1390/1390704.png"
                ),
                "embeds": [{
                    "title": "📅 Upcoming Earnings Reports",
                    "color": 3447003,
                    "fields": fields,
                }]
            },
        )
