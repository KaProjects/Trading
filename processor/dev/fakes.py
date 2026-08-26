import json
import logging
from datetime import date, timedelta
from decimal import Decimal
from typing import Any

from pydantic import BaseModel

from cmc.models import BitcoinQuote, FearAndGreedReading
from dev import data
from error_reporting import ErrorReporter
from firebase_repository import parse_company_snapshot
from gemini.client import InitialCompanyResult, QuarterReportResult
from gemini.institutions import (
    InstitutionRegistry,
    normalize_institution_name,
)
from gemini.models import (
    Company as GeminiCompany,
    CompanyTarget,
    InstitutionRecord,
    Quarter as GeminiQuarter,
    ReportDate,
    ReportDates,
    Target,
    Targets,
)
from gemini.service import create_target_id, required_company_fields
from myfinnhub.models import (
    Company as FinnhubCompany,
    Earnings,
    Quarter as FinnhubQuarter,
)
from polygon.models import (
    CompanyInsights,
    CompanyNewsHistory,
    CompanySentimentAnalysis,
    NewsResponse,
    NewsSentimentRecord,
    SentimentStatistics,
)
from polygon.service import company_path as polygon_company_path
from polygon.service import create_sentiment_analysis_id

logger = logging.getLogger(__name__)
SEPARATOR = "=" * 72


def _jsonable(value: Any) -> Any:
    if isinstance(value, BaseModel):
        return value.model_dump(mode="json")
    if isinstance(value, dict):
        return {
            str(key): _jsonable(item)
            for key, item in value.items()
        }
    if isinstance(value, (list, tuple)):
        return [_jsonable(item) for item in value]
    if isinstance(value, (date, Decimal)):
        return str(value)
    return value


def _log_exchange(action: str, target: str, payload: Any) -> None:
    formatted = json.dumps(
        _jsonable(payload),
        indent=2,
        sort_keys=True,
        ensure_ascii=False,
    )
    logger.info(
        "\n%s\n%s %s\n%s\n%s",
        SEPARATOR,
        action,
        target,
        formatted,
        SEPARATOR,
    )


def _log_operation(action: str, target: str) -> None:
    logger.info("%s %s", action, target)


class FakeCoinMarketCapClient:
    def __init__(self) -> None:
        self.reading = data.cmc_fear_and_greed()
        self.quote = data.cmc_bitcoin_quote()

    def get_fear_and_greed(self) -> FearAndGreedReading:
        result = self.reading.model_copy(deep=True)
        _log_operation("FAKE GET", "CoinMarketCap fear-and-greed")
        return result

    def get_btc_price(self) -> BitcoinQuote:
        result = self.quote.model_copy(deep=True)
        _log_operation("FAKE GET", "CoinMarketCap BTC quote")
        return result


class FakeGeminiClient:
    def get_initial_stock_data(self, ticker: str) -> InitialCompanyResult:
        result = data.gemini_initial_company(ticker)
        _log_operation("FAKE GET", f"Gemini initial stock data [{ticker}]")
        return InitialCompanyResult(company=result, errors=())

    def revalidate_report_dates(
        self,
        report_dates: ReportDates,
    ) -> ReportDates:
        updated = [
            report.model_copy(
                deep=True,
                update={
                    "report_date": (
                        report.report_date + timedelta(days=1)
                        if report.report_date is not None
                        else None
                    ),
                },
            )
            for report in report_dates.report_dates
        ]
        result = ReportDates(report_dates=updated)
        _log_operation("FAKE GET", "Gemini report-date revalidation")
        return result

    def get_quarter_report(
        self,
        ticker: str,
        current_quarter: GeminiQuarter,
        currency: str,
    ) -> QuarterReportResult:
        result = data.gemini_reported_quarter(current_quarter)
        _log_operation("FAKE GET", f"Gemini quarter report [{ticker}]")
        return QuarterReportResult(
            quarter=result,
            raw_response=result.model_dump_json(),
        )

    def get_price_targets(
        self,
        tickers: list[str],
        start_date: date,
        end_date: date,
    ) -> Targets:
        result = Targets(
            targets=data.gemini_price_targets(tickers, start_date, end_date)
        )
        _log_operation("FAKE GET", "Gemini institutional price targets")
        return result

    def get_target_report(self, target: Target) -> Target:
        result = target.model_copy(
            deep=True,
            update={"report": data.gemini_target_report(target)},
        )
        _log_operation(
            "FAKE GET",
            f"Gemini price target report [{target.ticker}]",
        )
        return result

    def get_news_sentiment_analysis(
        self,
        companies: list[CompanyInsights],
    ) -> list[CompanySentimentAnalysis]:
        result = [
            CompanySentimentAnalysis(
                ticker=company.ticker,
                statistics=SentimentStatistics.from_insights(
                    company.insights
                ),
                key_takeaways=(
                    [
                        f"{company.ticker} has "
                        f"{len(company.insights)} mapped news insight(s)."
                    ]
                    if company.insights
                    else []
                ),
            )
            for company in companies
        ]
        _log_operation("FAKE GET", "Gemini news sentiment analysis")
        return result


class FakeFinnhubClient:
    def __init__(self) -> None:
        self.earnings = data.finnhub_client_earnings()

    def get_earnings(self, company_id: str) -> dict[str, Earnings]:
        source = self.earnings.get(company_id)
        if source is None:
            source = data.finnhub_earnings_for(company_id)
        result = {
            quarter_id: earnings.model_copy(deep=True)
            for quarter_id, earnings in source.items()
        }
        _log_operation("FAKE GET", f"Finnhub earnings [{company_id}]")
        return result


class FakePolygonClient:
    def __init__(self, tickers: list[str] | None = None) -> None:
        self.news = data.polygon_news(tickers)

    def get_latest_news(
        self,
        *,
        previous_days: int | None = 7,
        ticker: str | None = None,
        limit: int = 1000,
    ) -> NewsResponse:
        _log_operation("FAKE GET", "Polygon latest news")
        return self.news.model_copy(deep=True)


class ConsoleDiscordClient:
    def post(self, channel_name: str, payload: object) -> None:
        _log_exchange(
            "FAKE POST",
            f"Discord channel #{channel_name}",
            payload,
        )

    def channel_exists(self, channel_name: str) -> bool:
        _log_operation("FAKE GET", f"Discord channel #{channel_name}")
        return True

    def post_if_channel_exists(
        self,
        channel_name: str,
        payload: object,
    ) -> str | None:
        if not self.channel_exists(channel_name):
            return None
        self.post(channel_name, payload)
        return (
            "https://discord.com/channels/dev-guild/"
            f"{channel_name.casefold()}/dev-message"
        )

    def post_btc(self, payload: object) -> None:
        _log_exchange("FAKE POST", "Discord webhook #btc", payload)

    def post_eventlog(self, payload: object) -> None:
        _log_exchange("FAKE POST", "Discord webhook #eventlog", payload)

    def post_earnings(self, payload: object) -> None:
        _log_exchange("FAKE POST", "Discord webhook #earnings", payload)

    def post_error(self, payload: object) -> None:
        _log_exchange("FAKE POST", "Discord error channel", payload)


class FakeGeminiFirebaseService:
    def __init__(
        self,
        snapshot: object,
        error_reporter: ErrorReporter | None = None,
    ) -> None:
        self.companies = parse_company_snapshot(
            snapshot,
            data_root="gemini",
            model=GeminiCompany,
            logger=logger,
            error_reporter=error_reporter,
            required_fields=required_company_fields,
        )
        registry = InstitutionRegistry({})
        for company in self.companies.values():
            if company is None:
                continue
            for target in company.targets.values():
                registry.resolve_or_create(target.institution)
        trusted_name = "Northstar Global Research"
        trusted_id = normalize_institution_name(trusted_name)
        trusted_institution = registry.resolve_or_create(trusted_name)
        registry.institutions[trusted_id] = trusted_institution.model_copy(
            update={"trusted": True},
        )
        self.institutions = registry.institutions

    def get_companies(self) -> dict[str, GeminiCompany | None]:
        result = {
            ticker: (
                company.model_copy(deep=True)
                if company is not None
                else None
            )
            for ticker, company in self.companies.items()
        }
        _log_operation("FAKE GET", "Firebase /company/*/gemini")
        return result

    def get_institutions(self) -> dict[str, InstitutionRecord]:
        _log_operation("FAKE GET", "Firebase /institution")
        return {
            institution_id: institution.model_copy(deep=True)
            for institution_id, institution in self.institutions.items()
        }

    def create_institutions(
        self,
        institutions: dict[str, InstitutionRecord],
    ) -> None:
        self.institutions.update({
            institution_id: institution.model_copy(deep=True)
            for institution_id, institution in institutions.items()
        })
        _log_exchange(
            "FAKE PATCH",
            "Firebase /institution",
            institutions,
        )

    def init_company(self, id: str, data: GeminiCompany) -> None:
        self.companies[id] = data.model_copy(deep=True)
        _log_exchange(
            "FAKE PUT",
            f"Firebase /company/{id}/gemini",
            data,
        )

    def update_report_date(self, new_report_date: ReportDate) -> None:
        company = self.companies.get(new_report_date.ticker)
        if company is None:
            raise KeyError(f"Unknown fake company: {new_report_date.ticker}")
        quarter = company.quarters.get(new_report_date.quarter)
        if quarter is None:
            raise KeyError(f"Unknown fake quarter: {new_report_date.quarter}")
        quarter.report_date_this_quarter = new_report_date.report_date
        company.info.last_update = date.today()
        _log_exchange(
            "FAKE PATCH",
            (
                f"Firebase /company/{new_report_date.ticker}/gemini/"
                f"quarters/{new_report_date.quarter}/report_date_this_quarter"
            ),
            new_report_date,
        )

    def report_quarter(
        self,
        company_id: str,
        quarter_data: GeminiQuarter,
    ) -> None:
        company = self._company(company_id)
        company.quarters[quarter_data.id] = quarter_data.model_copy(deep=True)
        company.info.last_update = date.today()
        _log_exchange(
            "FAKE PATCH",
            f"Firebase /company/{company_id}/gemini/quarters/{quarter_data.id}",
            quarter_data,
        )

    def create_quarter(
        self,
        company_id: str,
        new_quarter_data: GeminiQuarter,
    ) -> None:
        company = self._company(company_id)
        company.quarters[new_quarter_data.id] = new_quarter_data.model_copy(
            deep=True
        )
        company.info.current_quarter_id = new_quarter_data.id
        company.info.last_update = date.today()
        _log_exchange(
            "FAKE PATCH",
            (
                f"Firebase /company/{company_id}/gemini/quarters/"
                f"{new_quarter_data.id}"
            ),
            new_quarter_data,
        )

    def upsert_target(
        self,
        company_id: str,
        target: CompanyTarget,
    ) -> str:
        company = self._company(company_id)
        target_id = create_target_id(company_id, target)
        company.targets[target_id] = target.model_copy(deep=True)
        _log_exchange(
            "FAKE PUT",
            (
                f"Firebase /company/{company_id}/gemini/targets/"
                f"{target_id}"
            ),
            target,
        )
        return target_id

    def _company(self, company_id: str) -> GeminiCompany:
        company = self.companies.get(company_id)
        if company is None:
            raise KeyError(f"Unknown fake company: {company_id}")
        return company


class FakeFinnhubFirebaseService:
    def __init__(
        self,
        snapshot: object,
        error_reporter: ErrorReporter | None = None,
    ) -> None:
        self.companies = parse_company_snapshot(
            snapshot,
            data_root="fhe",
            model=FinnhubCompany,
            logger=logger,
            error_reporter=error_reporter,
        )

    def get_companies(self) -> dict[str, FinnhubCompany | None]:
        result = {
            ticker: (
                company.model_copy(deep=True)
                if company is not None
                else None
            )
            for ticker, company in self.companies.items()
        }
        _log_operation("FAKE GET", "Firebase /company/*/fhe")
        return result

    def init_company(
        self,
        company_id: str,
        earnings: dict[str, Earnings],
    ) -> None:
        snapshot = date.today().strftime("%Y%m%d")
        company = FinnhubCompany.model_validate({
            quarter_id: {snapshot: item}
            for quarter_id, item in earnings.items()
        })
        self.companies[company_id] = company
        _log_exchange(
            "FAKE PUT",
            f"Firebase /company/{company_id}/fhe",
            company,
        )

    def init_quarter(
        self,
        company_id: str,
        quarter_id: str,
        earnings: Earnings,
    ) -> None:
        company = self._company(company_id)
        snapshot = date.today().strftime("%Y%m%d")
        quarter = FinnhubQuarter.model_validate({
            snapshot: earnings,
        })
        company.root[quarter_id] = quarter
        _log_exchange(
            "FAKE PUT",
            f"Firebase /company/{company_id}/fhe/{quarter_id}",
            quarter,
        )

    def new_earnings(
        self,
        company_id: str,
        quarter_id: str,
        earnings: Earnings,
    ) -> None:
        company = self._company(company_id)
        snapshot = date.today().strftime("%Y%m%d")
        company.root[quarter_id].root[snapshot] = earnings.model_copy(deep=True)
        _log_exchange(
            "FAKE PUT",
            (
                f"Firebase /company/{company_id}/fhe/"
                f"{quarter_id}/{snapshot}"
            ),
            earnings,
        )

    def _company(self, company_id: str) -> FinnhubCompany:
        company = self.companies.get(company_id)
        if company is None:
            raise KeyError(f"Unknown fake company: {company_id}")
        return company


class FakePolygonFirebaseService:
    def __init__(
        self,
        snapshot: object,
        error_reporter: ErrorReporter | None = None,
    ) -> None:
        self.companies = parse_company_snapshot(
            snapshot,
            data_root="pgn",
            model=CompanyNewsHistory,
            logger=logger,
            error_reporter=error_reporter,
        )

    def get_companies(self) -> dict[str, CompanyNewsHistory | None]:
        result = {
            ticker: (
                company.model_copy(deep=True)
                if company is not None
                else None
            )
            for ticker, company in self.companies.items()
        }
        _log_operation("FAKE GET", "Firebase /company/*/pgn")
        return result

    def upsert_sentiment_analysis(
        self,
        analysis: CompanySentimentAnalysis,
    ) -> str:
        analysis_id = create_sentiment_analysis_id(analysis)
        record = NewsSentimentRecord.from_analysis(analysis)
        company = self.companies.get(analysis.ticker)
        if company is None:
            company = CompanyNewsHistory({})
            self.companies[analysis.ticker] = company
        company.root[analysis_id] = record
        _log_exchange(
            "FAKE PUT",
            f"Firebase /{polygon_company_path(analysis.ticker)}/{analysis_id}",
            record,
        )
        return analysis_id
