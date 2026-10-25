import logging
from unittest.mock import call, create_autospec

import pytest

from error_reporting import ErrorReporter
from gemini.client import GeminiClient
from discord.client import DiscordClient
from polygon.client import PolygonClient
from polygon.discord_templates import (
    eventlog_news_sentiment,
    ticker_news_sentiment,
)
from polygon.models import (
    CompanyNews,
    CompanySentimentAnalysis,
    NewsResponse,
    SentimentStatistics,
)
from polygon.retriever import PolygonNewsRetrieverRunner
from polygon.service import FirebaseService


def news_response() -> NewsResponse:
    return NewsResponse.model_validate({
        "count": 2,
        "status": "OK",
        "results": [
            {
                "id": "shared-article",
                "publisher": {"name": "Example Finance"},
                "title": "Apple and Microsoft announce product updates",
                "published_utc": "2026-08-26T12:00:00Z",
                "article_url": "https://example.com/shared-article",
                "tickers": ["AAPL", "MSFT", "GOOG"],
                "insights": [
                    {
                        "ticker": "AAPL",
                        "sentiment": "positive",
                        "sentiment_reasoning": "Demand remained strong.",
                    },
                    {
                        "ticker": "MSFT",
                        "sentiment": "neutral",
                        "sentiment_reasoning": "Guidance was unchanged.",
                    },
                    {
                        "ticker": "GOOG",
                        "sentiment": "negative",
                        "sentiment_reasoning": "Costs increased.",
                    },
                ],
            },
            {
                "id": "no-insights",
                "publisher": {"name": "Example Wire"},
                "title": "General market update",
                "published_utc": "2026-08-25T10:00:00Z",
                "article_url": "https://example.com/no-insights",
                "tickers": ["AAPL"],
                "insights": [],
            },
        ],
    })
@pytest.fixture
def runner():
    client = create_autospec(PolygonClient, instance=True)
    gemini = create_autospec(GeminiClient, instance=True)
    discord = create_autospec(DiscordClient, instance=True)
    service = create_autospec(FirebaseService, instance=True)
    errors = create_autospec(ErrorReporter, instance=True)
    instance = PolygonNewsRetrieverRunner(
        client=client,
        gemini=gemini,
        service=service,
        discord=discord,
        error_reporter=errors,
    )
    instance.log = create_autospec(logging.Logger, instance=True)
    return instance


def test_groups_insights_by_matching_firebase_company(runner):
    response = news_response()
    companies = {
        "AAPL": None,
        "MSFT": CompanyNews(),
        "NVDA": None,
    }

    mapped = runner._group_by_company(companies, response)

    assert set(mapped) == {"AAPL", "MSFT", "NVDA"}
    assert len(mapped["AAPL"].insights) == 1
    assert len(mapped["MSFT"].insights) == 1
    assert mapped["NVDA"].insights == []
    assert [insight.ticker for insight in mapped["AAPL"].insights] == [
        "AAPL"
    ]
    assert mapped["AAPL"].insights[0].sentiment_reasoning == (
        "Demand remained strong."
    )
    assert mapped["AAPL"].insights[0].article_id == "shared-article"
    assert [insight.ticker for insight in mapped["MSFT"].insights] == [
        "MSFT"
    ]
    assert len(response.results[0].insights) == 3


def test_groups_tickers_case_insensitively(runner):
    response = news_response()
    response.results[0].insights[0].ticker = "aapl"

    companies = runner._group_by_company({"AAPL": None}, response)

    assert len(companies["AAPL"].insights) == 1


def test_run_sends_sorted_company_insights_to_gemini(runner):
    runner.client.get_latest_news.return_value = news_response()
    runner.service.get_companies.return_value = {
        "MSFT": CompanyNews(),
        "AAPL": None,
        "NVDA": None,
    }
    analyses = [
        CompanySentimentAnalysis(
            ticker="AAPL",
            statistics=SentimentStatistics(
                total=1,
                positive=1,
            ),
            key_takeaways=["Demand remained strong."],
        ),
        CompanySentimentAnalysis(
            ticker="MSFT",
            statistics=SentimentStatistics(
                total=1,
                neutral=1,
            ),
            key_takeaways=["Guidance was unchanged."],
        ),
    ]
    runner.gemini.get_news_sentiment_analysis.return_value = analyses
    actions = []

    def persist(analysis):
        actions.append(f"persist:{analysis.ticker}")
        return f"analysis-{analysis.ticker}"

    def post(channel, payload):
        actions.append(f"post:{channel}")
        return "message-url"

    runner.service.upsert_sentiment_analysis.side_effect = persist
    runner.discord.post_if_channel_exists.side_effect = post

    result = runner.run()

    assert result == analyses
    runner.client.get_latest_news.assert_called_once_with()
    runner.service.get_companies.assert_called_once_with()
    sent_companies = (
        runner.gemini.get_news_sentiment_analysis.call_args.args[0]
    )
    assert [company.ticker for company in sent_companies] == [
        "AAPL",
        "MSFT",
    ]
    assert [len(company.insights) for company in sent_companies] == [
        1,
        1,
    ]
    assert runner.service.upsert_sentiment_analysis.call_args_list == [
        call(analyses[0]),
        call(analyses[1]),
    ]
    assert runner.discord.post_if_channel_exists.call_args_list == [
        call("AAPL", ticker_news_sentiment(analyses[0])),
        call("MSFT", ticker_news_sentiment(analyses[1])),
    ]
    assert actions == [
        "persist:AAPL",
        "persist:MSFT",
        "post:AAPL",
        "post:MSFT",
    ]
    runner.log.info.assert_any_call(
        "Polygon request returned %d article(s); "
        "%d unique article(s) retained",
        2,
        2,
    )
    runner.discord.post_eventlog.assert_called_once()
    embeds = runner.discord.post_eventlog.call_args.args[0]["embeds"]
    assert [embed["title"] for embed in embeds] == [
        "📰 Polygon news coverage",
        "🔥 Firebase companies",
        "🔎 Companies missing from Firebase",
    ]
    assert embeds[0]["description"] == "**2 articles returned**"
    assert embeds[1]["description"] == (
        "`AAPL`: **1 article**\n"
        "`MSFT`: **1 article**\n"
        "`NVDA`: **0 articles**"
    )
    assert embeds[1]["footer"]["text"] == (
        "2 total articles across 3 Firebase companies"
    )
    assert embeds[2]["description"] == (
        "No unmatched companies passed the filter."
    )
    assert embeds[2]["footer"]["text"] == (
        "1 total article before filtering | At least 5 articles; "
        "aliases and share classes excluded"
    )
    runner.errors.report.assert_not_called()


def test_failed_sentiment_persistence_is_reported_and_skipped(runner):
    failed = CompanySentimentAnalysis(
        ticker="AAPL",
        statistics=SentimentStatistics(total=1, positive=1),
        key_takeaways=["Demand remained strong."],
    )
    persisted = CompanySentimentAnalysis(
        ticker="MSFT",
        statistics=SentimentStatistics(total=1, neutral=1),
        key_takeaways=["Guidance was unchanged."],
    )
    error = RuntimeError("Firebase unavailable")
    runner.service.upsert_sentiment_analysis.side_effect = [
        error,
        "2026-08-26-a1b2c3",
    ]

    result = runner._persist_news_sentiment_analyses([
        failed,
        persisted,
    ])

    assert result == [persisted]
    runner.errors.report.assert_called_once_with(
        error,
        logger=runner.log,
        source=runner.name,
        operation="persist_news_sentiment_analysis",
        context={"ticker": "AAPL"},
    )


def test_news_sentiment_falls_back_to_eventlog(runner):
    analysis = CompanySentimentAnalysis(
        ticker="AAPL",
        statistics=SentimentStatistics(
            total=4,
            positive=2,
            neutral=1,
            mixed=1,
        ),
        key_takeaways=[
            "Demand remained strong.",
            "Execution risk remains.",
        ],
    )
    runner.discord.post_if_channel_exists.return_value = None

    runner._notify_news_sentiment_analyses([analysis])

    ticker_payload = ticker_news_sentiment(analysis)
    runner.discord.post_if_channel_exists.assert_called_once_with(
        "AAPL",
        ticker_payload,
    )
    runner.discord.post_eventlog.assert_called_once_with(
        eventlog_news_sentiment(analysis)
    )
    embed = runner.discord.post_eventlog.call_args.args[0]["embeds"][0]
    assert embed["title"] == "📰 AAPL | Weekly News Sentiment Analysis"
    assert embed["description"] == (
        "**Statistics**\n"
        "Total: **4** | Positive: **2** | Neutral: **1** | Mixed: **1**"
        "\n\n**Key takeaways**\n"
        "- Demand remained strong.\n"
        "- Execution risk remains."
    )
    runner.errors.report.assert_not_called()


def test_unmapped_stats_count_before_filtering(runner):
    response = news_response()
    template = response.results[0]
    response.results = []
    for index in range(5):
        article = template.model_copy(deep=True)
        article.id = f"frequent-{index}"
        article.tickers = [
            "TSLA",
            "TSLA",
            "GOOGM",
            "JPM-PC",
            "BRK.B",
            "ORCLPD",
        ]
        article.insights = []
        response.results.append(article)
    for index in range(4):
        article = template.model_copy(deep=True)
        article.id = f"infrequent-{index}"
        article.tickers = ["ORCL"]
        article.insights = []
        response.results.append(article)

    counts = runner._count_unmapped_articles_by_ticker(
        {"AAPL": None},
        response,
    )

    assert counts == {
        "BRK.B": 5,
        "GOOGM": 5,
        "JPM-PC": 5,
        "ORCL": 4,
        "ORCLPD": 5,
        "TSLA": 5,
    }
    assert sum(counts.values()) == 29
    assert runner._filter_unmapped_article_counts(counts) == {"TSLA": 5}


def test_run_posts_filtered_unmatched_stats_to_eventlog(runner):
    response = news_response()
    template = response.results[0]
    response.results = []
    for index in range(5):
        article = template.model_copy(deep=True)
        article.id = f"unmatched-{index}"
        article.tickers = ["TSLA"]
        article.insights = []
        response.results.append(article)
    runner.client.get_latest_news.return_value = response
    runner.service.get_companies.return_value = {"AAPL": None}

    runner.run()

    runner.discord.post_eventlog.assert_called_once()
    embeds = runner.discord.post_eventlog.call_args.args[0]["embeds"]
    assert embeds[0]["description"] == "**5 articles returned**"
    assert embeds[1]["description"] == "`AAPL`: **0 articles**"
    assert embeds[1]["footer"]["text"] == (
        "0 total articles across 1 Firebase company"
    )
    assert embeds[2]["description"] == "`TSLA`: **5 articles**"
    assert embeds[2]["footer"]["text"] == (
        "5 total articles before filtering | At least 5 articles; "
        "aliases and share classes excluded"
    )
    runner.gemini.get_news_sentiment_analysis.assert_not_called()


def test_article_merge_deduplicates_article_and_company_insights(runner):
    original = news_response().results[0]
    duplicate = original.model_copy(deep=True)
    duplicate.tickers.append("NVDA")
    duplicate.insights.extend([
        duplicate.insights[0].model_copy(deep=True),
        duplicate.insights[0].model_copy(
            update={"ticker": "NVDA"},
        ),
    ])
    articles = {original.id: original.model_copy(deep=True)}

    runner._merge_articles(
        articles,
        NewsResponse(results=[duplicate]),
    )

    assert list(articles) == ["shared-article"]
    assert [
        insight.ticker
        for insight in articles["shared-article"].insights
    ] == ["AAPL", "MSFT", "GOOG", "NVDA"]


def test_run_reports_failure_and_returns_empty_list(runner):
    error = RuntimeError("Polygon unavailable")
    runner.client.get_latest_news.side_effect = error

    result = runner.run()

    assert result == []
    runner.service.get_companies.assert_not_called()
    runner.gemini.get_news_sentiment_analysis.assert_not_called()
    runner.errors.report.assert_called_once_with(
        error,
        logger=runner.log,
        source=runner.name,
        operation="run",
    )
