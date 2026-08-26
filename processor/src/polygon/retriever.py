import logging
from collections import Counter
from collections.abc import Mapping

from discord.client import DiscordClient
from error_reporting import ErrorReporter
from gemini.client import GeminiClient
from polygon.client import PolygonClient
from polygon.discord_templates import (
    eventlog_news_sentiment,
    news_coverage_reports,
    ticker_news_sentiment,
)
from polygon.models import (
    CompanyInsights,
    CompanyNews,
    CompanyNewsInsight,
    CompanySentimentAnalysis,
    NewsArticle,
    NewsResponse,
)
from polygon.service import FirebaseService

RUNNER_NAME = "PolygonNews"
UNMAPPED_MIN_ARTICLE_COUNT = 5
UNMAPPED_IGNORED_TICKER_PREFIXES = ("GOOG", "JPM", "BRK.", "ORCL")
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
        discord: DiscordClient | None = None,
        error_reporter: ErrorReporter | None = None,
    ) -> None:
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
            )
        if discord is None:
            raise ValueError("discord is required")

        self.errors = error_reporter or ErrorReporter(environment="local")
        self.client = client
        self.gemini = gemini
        self.discord = discord
        self.service = service or FirebaseService(
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
            if not mapped_with_insights:
                return []
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
            return analyses
        except Exception as exception:
            self.errors.report(
                exception,
                logger=self.log,
                source=self.name,
                operation="run",
            )
            return []

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
