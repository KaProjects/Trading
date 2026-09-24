import logging
from collections import Counter
from collections.abc import Mapping
from datetime import date, timedelta

from discord.client import DiscordClient
from error_reporting import ErrorReporter
from gemini.client import GeminiClient
from gemini.models import BullBearContext, CompanyTarget, Quarter
from gemini.service import FirebaseService as GeminiFirebaseService
from myfinnhub.models import Company as FinnhubCompany
from myfinnhub.service import FirebaseService as FinnhubFirebaseService
from polygon.client import PolygonClient
from polygon.discord_templates import (
    eventlog_news_sentiment,
    news_coverage_reports,
    ticker_news_sentiment,
)
from polygon.models import (
    CompanyInsights,
    CompanyNews,
    CompanyNewsHistory,
    CompanyNewsInsight,
    CompanySentimentAnalysis,
    NewsArticle,
    NewsResponse,
)
from polygon.service import FirebaseService

RUNNER_NAME = "PolygonNews"
UNMAPPED_MIN_ARTICLE_COUNT = 5
UNMAPPED_IGNORED_TICKER_PREFIXES = ("GOOG", "JPM", "BRK.", "ORCL")
ONBOARDING_NEWS_LOOKBACK_DAYS = 30
BULL_BEAR_RESEARCH_MIN_LOOKBACK_DAYS = 30
BULL_BEAR_FINANCIALS_QUARTER_COUNT = 4
logger = logging.getLogger(RUNNER_NAME)


class PolygonNewsRetrieverRunner:
    log = logger
    name = RUNNER_NAME
    model = "gemini-3.1-pro-preview"

    def __init__(
        self,
        polygon_api_key: str | None = None,
        gemini_api_key: str | None = None,
        client: PolygonClient | None = None,
        gemini: GeminiClient | None = None,
        service: FirebaseService | None = None,
        gemini_service: GeminiFirebaseService | None = None,
        finnhub_service: FinnhubFirebaseService | None = None,
        discord: DiscordClient | None = None,
        error_reporter: ErrorReporter | None = None,
    ) -> None:
        self.errors = error_reporter or ErrorReporter(environment="local")
        if client is None:
            if polygon_api_key is None:
                raise ValueError(
                    "polygon_api_key is required without a client"
                )
            client = PolygonClient(api_key=polygon_api_key)
        if gemini is None:
            if gemini_api_key is None:
                raise ValueError(
                    "gemini_api_key is required without a Gemini client"
                )
            gemini = GeminiClient(
                api_key=gemini_api_key,
                model=self.model,
                error_reporter=self.errors,
            )
        if discord is None:
            raise ValueError("discord is required")

        self.client = client
        self.gemini = gemini
        self.discord = discord
        self.service = service or FirebaseService(
            error_reporter=self.errors,
        )
        self.gemini_service = gemini_service or GeminiFirebaseService(
            error_reporter=self.errors,
        )
        self.finnhub_service = finnhub_service or FinnhubFirebaseService(
            error_reporter=self.errors,
        )

    def run(self) -> list[CompanySentimentAnalysis]:
        try:
            response = self.client.get_latest_news()
            polygon_article_count = len(response.results)
            response = self._deduplicate_news_response(response)
            companies = self.service.get_companies()
            mapped = self._group_by_company(companies, response)
            self._notify_news_coverage(
                companies,
                mapped,
                response,
                polygon_article_count=polygon_article_count,
            )
            mapped_with_insights = {
                ticker: company
                for ticker, company in mapped.items()
                if company.insights
            }
            self.log.info(
                "Mapped %d news insight(s) across %d of %d "
                "Firebase companies",
                sum(
                    len(company.insights)
                    for company in mapped_with_insights.values()
                ),
                len(mapped_with_insights),
                len(mapped),
            )
            analyses: list[CompanySentimentAnalysis] = []
            if mapped_with_insights:
                analyses = self.gemini.get_news_sentiment_analysis([
                    CompanyInsights(
                        ticker=company_id,
                        insights=company.insights,
                    )
                    for company_id, company in sorted(
                        mapped_with_insights.items()
                    )
                ])
                persisted_analyses = self._persist_news_sentiment_analyses(
                    analyses
                )
                self._notify_news_sentiment_analyses(persisted_analyses)

            self._generate_bull_bear_cases(companies)
            return analyses
        except Exception as exception:
            self.errors.report(
                exception,
                logger=self.log,
                source=self.name,
                operation="run",
            )
            return []

    def process_company(
        self,
        ticker: str,
        *,
        previous_days: int | None = 7,
    ) -> None:
        response = self.client.get_latest_news(
            ticker=ticker,
            previous_days=previous_days,
        )
        response = self._deduplicate_news_response(response)
        company_news = self._group_by_company({ticker: None}, response)[
            ticker
        ]
        if not company_news.insights:
            return

        analyses = self.gemini.get_news_sentiment_analysis([
            CompanyInsights(ticker=ticker, insights=company_news.insights),
        ])
        persisted_analyses = self._persist_news_sentiment_analyses(analyses)
        self._notify_news_sentiment_analyses(persisted_analyses)

    def _generate_bull_bear_cases(
        self,
        companies: Mapping[str, CompanyNewsHistory | None],
    ) -> None:
        try:
            gemini_companies = self.gemini_service.get_companies()
            finnhub_companies = self.finnhub_service.get_companies()

            contexts: list[BullBearContext] = []
            for ticker in sorted(companies):
                gemini_company = gemini_companies.get(ticker)
                if gemini_company is None:
                    continue
                latest_quarter = self._latest_reported_quarter(
                    gemini_company.quarters
                )
                if (
                    latest_quarter is None
                    or latest_quarter.report_date_this_quarter is None
                ):
                    continue

                since = max(
                    latest_quarter.report_date_this_quarter,
                    date.today()
                    - timedelta(days=BULL_BEAR_RESEARCH_MIN_LOOKBACK_DAYS),
                )
                research = self._build_bull_bear_research(
                    recent_quarters=self._recent_reported_quarters(
                        gemini_company.quarters,
                        count=BULL_BEAR_FINANCIALS_QUARTER_COUNT,
                    ),
                    targets=gemini_company.targets,
                    sentiment_history=companies.get(ticker),
                    estimates=finnhub_companies.get(ticker),
                    current_quarter_id=gemini_company.info.current_quarter_id,
                    since=since,
                )
                contexts.append(BullBearContext(
                    ticker=ticker,
                    period=latest_quarter.id,
                    research=research,
                ))

            if not contexts:
                return

            cases = self.gemini.get_bull_bear_cases(contexts)
            for case in cases:
                try:
                    self.gemini_service.upsert_bull_bear(case.ticker, case)
                except Exception as exception:
                    self.errors.report(
                        exception,
                        logger=self.log,
                        source=self.name,
                        operation="persist_bull_bear_case",
                        context={"ticker": case.ticker},
                    )
        except Exception as exception:
            self.errors.report(
                exception,
                logger=self.log,
                source=self.name,
                operation="generate_bull_bear_cases",
                context={"company_count": str(len(companies))},
            )

    @staticmethod
    def _latest_reported_quarter(
        quarters: Mapping[str, Quarter],
    ) -> Quarter | None:
        reported = [
            quarter
            for quarter in quarters.values()
            if quarter.reported_revenues is not None
        ]
        if not reported:
            return None
        return max(reported, key=lambda quarter: quarter.id)

    @staticmethod
    def _recent_reported_quarters(
        quarters: Mapping[str, Quarter],
        *,
        count: int,
    ) -> list[Quarter]:
        reported = sorted(
            (
                quarter
                for quarter in quarters.values()
                if quarter.reported_revenues is not None
            ),
            key=lambda quarter: quarter.id,
        )
        return reported[-count:]

    @classmethod
    def _build_bull_bear_research(
        cls,
        *,
        recent_quarters: list[Quarter],
        targets: Mapping[str, CompanyTarget],
        sentiment_history: CompanyNewsHistory | None,
        estimates: FinnhubCompany | None,
        current_quarter_id: str,
        since: date,
    ) -> str:
        sections = [
            cls._format_reported_financials(recent_quarters),
            cls._format_forward_estimates(estimates, current_quarter_id),
            cls._format_institutional_targets(targets, since),
            cls._format_weekly_sentiment(sentiment_history, since),
        ]
        return "\n\n".join(section for section in sections if section)

    @staticmethod
    def _format_reported_financials(quarters: list[Quarter]) -> str:
        lines = []
        for quarter in quarters:
            fields = [
                ("revenue", quarter.reported_revenues),
                ("gross profit", quarter.reported_gross_profit),
                ("operating income", quarter.reported_operating_income),
                ("net income", quarter.reported_net_income),
                ("capital expenditures", quarter.reported_capex),
                ("free cash flow", quarter.reported_free_cash_flow),
                ("dividend", quarter.reported_div),
                ("shares", quarter.reported_shares),
                ("earnings per share", quarter.reported_eps),
            ]
            values = ", ".join(
                f"{label} {value}"
                for label, value in fields
                if value is not None
            )
            lines.append(f"{quarter.id}: {values or 'not reported yet'}")
        return (
            "REPORTED FINANCIALS BY QUARTER (millions of the reporting "
            "currency), oldest to newest:\n" + "\n".join(lines)
        )

    @staticmethod
    def _format_forward_estimates(
        estimates: FinnhubCompany | None,
        current_quarter_id: str,
    ) -> str | None:
        if estimates is None:
            return None

        lines = []
        for quarter_id in sorted(estimates.root):
            if quarter_id < current_quarter_id:
                continue
            snapshots = estimates.root[quarter_id].root
            if not snapshots:
                continue
            latest = snapshots[max(snapshots)]
            parts = []
            if latest.epse is not None:
                parts.append(f"estimated EPS {latest.epse}")
            if latest.reve is not None:
                parts.append(f"estimated revenue {latest.reve}")
            if not parts:
                continue
            lines.append(f"{quarter_id}: {', '.join(parts)}")

        if not lines:
            return None
        return "FORWARD ESTIMATES (not yet reported):\n" + "\n".join(lines)

    @staticmethod
    def _format_institutional_targets(
        targets: Mapping[str, CompanyTarget],
        since: date,
    ) -> str | None:
        recent = sorted(
            (
                target
                for target in targets.values()
                if target.date >= since
            ),
            key=lambda target: target.date,
            reverse=True,
        )
        if not recent:
            return None

        lines = []
        for target in recent:
            lines.append(
                f"- {target.institution}, {target.date.isoformat()}, "
                f"{target.rating or 'no rating stated'}"
            )
            if target.report is not None:
                lines.append(f"  {target.report.overview}")
                for takeaway in target.report.key_takeaways:
                    lines.append(f"  * {takeaway}")
        return (
            "INSTITUTIONAL RESEARCH SINCE THE LAST REPORT:\n"
            + "\n".join(lines)
        )

    @staticmethod
    def _format_weekly_sentiment(
        sentiment_history: CompanyNewsHistory | None,
        since: date,
    ) -> str | None:
        if sentiment_history is None:
            return None

        entries = []
        for key, record in sentiment_history.root.items():
            try:
                entry_date = date.fromisoformat(key[:10])
            except ValueError:
                continue
            if entry_date < since:
                continue
            entries.append((entry_date, record))
        if not entries:
            return None
        entries.sort(key=lambda item: item[0], reverse=True)

        lines = []
        for entry_date, record in entries:
            counts = ", ".join(
                f"{label}={count}"
                for label, count in record.sentiment.items()
            )
            lines.append(f"- {entry_date.isoformat()}: {{{counts}}}")
            for takeaway in record.key_takeaways:
                lines.append(f"  * {takeaway}")
        return (
            "WEEKLY NEWS SENTIMENT SINCE THE LAST REPORT:\n"
            + "\n".join(lines)
        )

    def _deduplicate_news_response(
        self,
        response: NewsResponse,
    ) -> NewsResponse:
        articles: dict[str, NewsArticle] = {}
        self._merge_articles(articles, response)
        self.log.info(
            "Polygon request returned %d article(s); "
            "%d unique article(s) retained",
            len(response.results),
            len(articles),
        )
        return self._merged_news_response(
            response,
            articles,
        )

    @staticmethod
    def _merge_articles(
        articles: dict[str, NewsArticle],
        response: NewsResponse,
    ) -> None:
        for article in response.results:
            existing = articles.get(article.id)
            if existing is None:
                articles[article.id] = article.model_copy(deep=True)
                continue

            merged = existing.model_copy(deep=True)
            known_tickers = {
                insight.ticker.casefold()
                for insight in merged.insights
            }
            for insight in article.insights:
                normalized_ticker = insight.ticker.casefold()
                if normalized_ticker in known_tickers:
                    continue
                merged.insights.append(insight.model_copy(deep=True))
                known_tickers.add(normalized_ticker)
            merged.tickers = list(dict.fromkeys([
                *merged.tickers,
                *article.tickers,
            ]))
            articles[article.id] = merged

    @staticmethod
    def _merged_news_response(
        initial_response: NewsResponse,
        articles: dict[str, NewsArticle],
    ) -> NewsResponse:
        return initial_response.model_copy(
            deep=True,
            update={
                "count": len(articles),
                "next_url": None,
                "results": list(articles.values()),
            },
        )

    @staticmethod
    def _group_by_company(
        companies: Mapping[str, object | None],
        response: NewsResponse,
    ) -> dict[str, CompanyNews]:
        result = {
            company_id: CompanyNews()
            for company_id in companies
        }
        company_ids = {
            company_id.casefold(): company_id
            for company_id in companies
        }

        for article in response.results:
            mapped_company_ids: set[str] = set()
            for insight in article.insights:
                company_id = company_ids.get(insight.ticker.casefold())
                if (
                    company_id is None
                    or company_id in mapped_company_ids
                ):
                    continue
                result[company_id].insights.append(
                    CompanyNewsInsight(
                        article_id=article.id,
                        **insight.model_dump(),
                    )
                )
                mapped_company_ids.add(company_id)

        return result

    def _notify_news_coverage(
        self,
        companies: Mapping[str, object | None],
        mapped: dict[str, CompanyNews],
        response: NewsResponse,
        *,
        polygon_article_count: int,
    ) -> None:
        all_counts = self._count_unmapped_articles_by_ticker(
            companies,
            response,
        )
        filtered_counts = self._filter_unmapped_article_counts(all_counts)
        firebase_counts = dict(sorted(
            (
                (ticker, len(company.insights))
                for ticker, company in mapped.items()
            ),
            key=lambda item: (-item[1], item[0]),
        ))
        unmatched_counts = dict(sorted(
            filtered_counts.items(),
            key=lambda item: (-item[1], item[0]),
        ))
        payloads = news_coverage_reports(
            polygon_article_count=polygon_article_count,
            firebase_counts=firebase_counts,
            total_firebase_article_count=sum(firebase_counts.values()),
            unmatched_counts=unmatched_counts,
            total_unmatched_article_count=sum(all_counts.values()),
        )
        for message_index, payload in enumerate(payloads, start=1):
            try:
                self.discord.post_eventlog(payload)
            except Exception as exception:
                self.errors.report(
                    exception,
                    logger=self.log,
                    source=self.name,
                    operation="notify_news_coverage",
                    context={
                        "firebase_ticker_count": len(firebase_counts),
                        "unmatched_ticker_count": len(unmatched_counts),
                        "message_index": message_index,
                        "message_count": len(payloads),
                    },
                )

    def _notify_news_sentiment_analyses(
        self,
        analyses: list[CompanySentimentAnalysis],
    ) -> None:
        for analysis in analyses:
            try:
                if self.discord.post_if_channel_exists(
                    analysis.ticker,
                    ticker_news_sentiment(analysis),
                ):
                    continue
                self.discord.post_eventlog(
                    eventlog_news_sentiment(analysis)
                )
            except Exception as exception:
                self.errors.report(
                    exception,
                    logger=self.log,
                    source=self.name,
                    operation="notify_news_sentiment_analysis",
                    context={"ticker": analysis.ticker},
                )

    def _persist_news_sentiment_analyses(
        self,
        analyses: list[CompanySentimentAnalysis],
    ) -> list[CompanySentimentAnalysis]:
        persisted = []
        for analysis in analyses:
            try:
                self.service.upsert_sentiment_analysis(analysis)
                persisted.append(analysis)
            except Exception as exception:
                self.errors.report(
                    exception,
                    logger=self.log,
                    source=self.name,
                    operation="persist_news_sentiment_analysis",
                    context={"ticker": analysis.ticker},
                )
        return persisted

    @staticmethod
    def _count_unmapped_articles_by_ticker(
        companies: Mapping[str, object | None],
        response: NewsResponse,
    ) -> Counter[str]:
        firebase_tickers = {
            ticker.casefold()
            for ticker in companies
        }
        counts: Counter[str] = Counter()

        for article in response.results:
            article_tickers = {
                ticker.strip().upper()
                for ticker in article.tickers
                if ticker.strip()
            }
            article_tickers.update(
                insight.ticker.strip().upper()
                for insight in article.insights
                if insight.ticker.strip()
            )
            counts.update(
                ticker
                for ticker in article_tickers
                if ticker.casefold() not in firebase_tickers
            )

        return counts

    @staticmethod
    def _filter_unmapped_article_counts(
        counts: Counter[str],
    ) -> Counter[str]:
        return Counter({
            ticker: count
            for ticker, count in counts.items()
            if count >= UNMAPPED_MIN_ARTICLE_COUNT
            and not ticker.startswith(UNMAPPED_IGNORED_TICKER_PREFIXES)
        })
