import logging
from datetime import date, timedelta
from unittest.mock import call, create_autospec

import pytest

from error_reporting import ErrorReporter
from gemini.client import GeminiClient
from gemini.models import (
    Company as GeminiCompany,
    CompanyBullBear,
    CompanyTarget,
    Info as GeminiInfo,
    Quarter as GeminiQuarter,
    TargetReport,
)
from gemini.service import FirebaseService as GeminiFirebaseService
from discord.client import DiscordClient
from myfinnhub.models import (
    Company as FinnhubCompany,
    Earnings,
    Quarter as FinnhubQuarter,
)
from myfinnhub.service import FirebaseService as FinnhubFirebaseService
from polygon.client import PolygonClient
from polygon.discord_templates import (
    eventlog_news_sentiment,
    ticker_news_sentiment,
)
from polygon.models import (
    CompanyNews,
    CompanyNewsHistory,
    CompanySentimentAnalysis,
    NewsResponse,
    NewsSentimentRecord,
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
    gemini_service = create_autospec(GeminiFirebaseService, instance=True)
    gemini_service.get_companies.return_value = {}
    finnhub_service = create_autospec(FinnhubFirebaseService, instance=True)
    finnhub_service.get_companies.return_value = {}
    errors = create_autospec(ErrorReporter, instance=True)
    instance = PolygonNewsRetrieverRunner(
        client=client,
        gemini=gemini,
        service=service,
        gemini_service=gemini_service,
        finnhub_service=finnhub_service,
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


def test_process_company_fetches_scoped_news_and_persists_sentiment(runner):
    runner.client.get_latest_news.return_value = news_response()
    analysis = CompanySentimentAnalysis(
        ticker="AAPL",
        statistics=SentimentStatistics(total=1, positive=1),
        key_takeaways=["Demand remained strong."],
    )
    runner.gemini.get_news_sentiment_analysis.return_value = [analysis]
    runner.discord.post_if_channel_exists.return_value = "message-url"

    runner.process_company("AAPL")

    runner.client.get_latest_news.assert_called_once_with(
        ticker="AAPL",
        previous_days=7,
    )
    sent_companies = (
        runner.gemini.get_news_sentiment_analysis.call_args.args[0]
    )
    assert [company.ticker for company in sent_companies] == ["AAPL"]
    assert len(sent_companies[0].insights) == 1
    runner.service.upsert_sentiment_analysis.assert_called_once_with(
        analysis
    )
    runner.discord.post_if_channel_exists.assert_called_once_with(
        "AAPL",
        ticker_news_sentiment(analysis),
    )
    runner.discord.post_eventlog.assert_not_called()


def test_process_company_skips_gemini_when_ticker_has_no_insights(runner):
    empty_response = NewsResponse.model_validate({
        "count": 0,
        "status": "OK",
        "results": [],
    })
    runner.client.get_latest_news.return_value = empty_response

    runner.process_company("NVDA")

    runner.client.get_latest_news.assert_called_once_with(
        ticker="NVDA",
        previous_days=7,
    )
    runner.gemini.get_news_sentiment_analysis.assert_not_called()
    runner.service.upsert_sentiment_analysis.assert_not_called()
    runner.discord.post_if_channel_exists.assert_not_called()
    runner.discord.post_eventlog.assert_not_called()


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


def make_gemini_quarter(**overrides):
    data = {
        "name": "Q3 2026",
        "id": "26Q3",
        "ending_month": "26-09",
        "report_date_previous_quarter": "2026-06-01",
        "report_date_this_quarter": "2026-09-09",
    }
    data.update(overrides)
    return GeminiQuarter(**data)


def make_gemini_company(
    ticker="AAPL",
    current_quarter_id="26Q4",
    quarters=None,
    targets=None,
):
    return GeminiCompany(
        info=GeminiInfo(
            ticker=ticker,
            last_update="2026-09-09",
            current_quarter_id=current_quarter_id,
        ),
        quarters=quarters or {},
        targets=targets or {},
    )


def make_earnings(report, **overrides):
    data = {"epsa": None, "epse": None, "reva": None, "reve": None}
    data.update(overrides)
    return Earnings(report=report, **data)


class TestGenerateBullBearCases:
    def test_financials_section_includes_a_capped_recent_quarter_trend(
        self,
        runner,
    ):
        quarters = {
            quarter_id: make_gemini_quarter(
                id=quarter_id,
                reported_revenues=str(revenue),
            )
            for quarter_id, revenue in [
                ("25Q3", 40000),
                ("25Q4", 41000),
                ("26Q1", 42000),
                ("26Q2", 43000),
                ("26Q3", 44000),
            ]
        }

        recent = runner._recent_reported_quarters(
            quarters,
            count=4,
        )

        assert [quarter.id for quarter in recent] == [
            "25Q4", "26Q1", "26Q2", "26Q3",
        ]

        research = runner._format_reported_financials(recent)
        assert research.startswith(
            "REPORTED FINANCIALS BY QUARTER (millions of the reporting "
            "currency), oldest to newest:"
        )
        assert "25Q3" not in research
        assert "25Q4: revenue 41000" in research
        assert "26Q3: revenue 44000" in research
        lines = research.splitlines()[1:]
        assert [line.split(":")[0] for line in lines] == [
            "25Q4", "26Q1", "26Q2", "26Q3",
        ]

    def test_builds_context_from_all_three_sources_and_persists_result(
        self,
        runner,
    ):
        today = date.today()
        report_date = today - timedelta(days=5)
        recent_target_date = today - timedelta(days=3)
        old_target_date = report_date - timedelta(days=1)
        recent_sentiment_key = f"{(today - timedelta(days=1)).isoformat()}-abc123"
        old_sentiment_key = f"{(report_date - timedelta(days=1)).isoformat()}-def456"

        quarter = make_gemini_quarter(
            report_date_this_quarter=report_date.isoformat(),
            reported_revenues="46740",
            reported_net_income="19310",
        )
        target_with_report = CompanyTarget(
            institution="Morgan Stanley",
            date=recent_target_date.isoformat(),
            price="250",
            rating="Overweight",
            source="https://research.example.com/aapl",
            report=TargetReport(
                overview="Agentic seats beat plan.",
                key_takeaways=["Data cloud attach improved."],
            ),
        )
        old_target = CompanyTarget(
            institution="UBS",
            date=old_target_date.isoformat(),
            price="200",
            rating="Neutral",
            source="https://research.example.com/aapl-old",
        )
        gemini_company = make_gemini_company(
            current_quarter_id="26Q4",
            quarters={"26Q3": quarter},
            targets={"t1": target_with_report, "t2": old_target},
        )
        runner.gemini_service.get_companies.return_value = {
            "AAPL": gemini_company,
        }

        finnhub_company = FinnhubCompany(root={
            "26Q3": FinnhubQuarter(root={
                "20260901": make_earnings(
                    "2026-09-01-bmo", epsa="1.2", reva="46700",
                ),
            }),
            "26Q4": FinnhubQuarter(root={
                "20260901": make_earnings(
                    "2026-09-01-bmo", epse="1.4", reve="48000",
                ),
                "20260910": make_earnings(
                    "2026-09-10-bmo", epse="1.5", reve="48500",
                ),
            }),
        })
        runner.finnhub_service.get_companies.return_value = {
            "AAPL": finnhub_company,
        }

        sentiment_history = CompanyNewsHistory(root={
            recent_sentiment_key: NewsSentimentRecord(
                sentiment={"positive": 22, "neutral": 13, "negative": 4},
                key_takeaways=["Data center momentum is strong."],
            ),
            old_sentiment_key: NewsSentimentRecord(
                sentiment={"positive": 2, "neutral": 6, "negative": 3},
                key_takeaways=["Outdated context."],
            ),
        })
        companies = {"AAPL": sentiment_history}

        result_case = CompanyBullBear(
            ticker="AAPL",
            bull=[{"point": "Demand strong", "reasoning": "Because X."}],
            bear=[{"point": "Margins soft", "reasoning": "Because Y."}],
        )
        runner.gemini.get_bull_bear_cases.return_value = [result_case]

        runner._generate_bull_bear_cases(companies)

        runner.gemini.get_bull_bear_cases.assert_called_once()
        contexts = runner.gemini.get_bull_bear_cases.call_args.args[0]
        assert [context.ticker for context in contexts] == ["AAPL"]
        research = contexts[0].research
        assert contexts[0].period == "26Q3"

        assert "REPORTED FINANCIALS BY QUARTER" in research
        assert "26Q3: revenue 46740" in research
        assert "revenue 46740" in research
        assert "net income 19310" in research

        assert "FORWARD ESTIMATES" in research
        assert "26Q4: estimated EPS 1.5, estimated revenue 48500" in research
        assert "26Q3" not in research.split("FORWARD ESTIMATES")[1].split(
            "INSTITUTIONAL"
        )[0]

        assert "Morgan Stanley" in research
        assert "Agentic seats beat plan." in research
        assert "Data cloud attach improved." in research
        assert "UBS" not in research

        assert "Data center momentum is strong." in research
        assert "Outdated context." not in research

        runner.gemini_service.upsert_bull_bear.assert_called_once_with(
            "AAPL",
            result_case,
        )
        runner.errors.report.assert_not_called()

    def test_runs_for_enabled_companies_without_fresh_news(self, runner):
        today = date.today()
        report_date = today - timedelta(days=5)
        quarter = make_gemini_quarter(
            report_date_this_quarter=report_date.isoformat(),
            reported_revenues="1000",
        )
        gemini_company = make_gemini_company(
            current_quarter_id="26Q4",
            quarters={"26Q3": quarter},
        )
        runner.gemini_service.get_companies.return_value = {
            "AAPL": gemini_company,
        }
        runner.finnhub_service.get_companies.return_value = {}
        runner.gemini.get_bull_bear_cases.return_value = [
            CompanyBullBear(
                ticker="AAPL",
                bull=[{"point": "p", "reasoning": "r"}],
                bear=[{"point": "p", "reasoning": "r"}],
            ),
        ]

        # No sentiment history at all this ticker - still processed.
        runner._generate_bull_bear_cases({"AAPL": None})

        runner.gemini.get_bull_bear_cases.assert_called_once()
        runner.gemini_service.upsert_bull_bear.assert_called_once()

    def test_skips_company_with_no_gemini_data(self, runner):
        runner.gemini_service.get_companies.return_value = {"AAPL": None}
        runner.finnhub_service.get_companies.return_value = {}

        runner._generate_bull_bear_cases({"AAPL": None})

        runner.gemini.get_bull_bear_cases.assert_not_called()

    def test_skips_company_with_no_reported_quarter(self, runner):
        unreported_quarter = make_gemini_quarter(
            reported_revenues=None,
            report_date_this_quarter=None,
        )
        gemini_company = make_gemini_company(
            quarters={"26Q3": unreported_quarter},
        )
        runner.gemini_service.get_companies.return_value = {
            "AAPL": gemini_company,
        }
        runner.finnhub_service.get_companies.return_value = {}

        runner._generate_bull_bear_cases({"AAPL": None})

        runner.gemini.get_bull_bear_cases.assert_not_called()

    def test_reports_failure_when_gemini_call_fails(self, runner):
        report_date = date.today() - timedelta(days=5)
        quarter = make_gemini_quarter(
            report_date_this_quarter=report_date.isoformat(),
            reported_revenues="1000",
        )
        gemini_company = make_gemini_company(quarters={"26Q3": quarter})
        runner.gemini_service.get_companies.return_value = {
            "AAPL": gemini_company,
        }
        runner.finnhub_service.get_companies.return_value = {}
        error = RuntimeError("Gemini unavailable")
        runner.gemini.get_bull_bear_cases.side_effect = error

        runner._generate_bull_bear_cases({"AAPL": None})

        runner.errors.report.assert_called_once_with(
            error,
            logger=runner.log,
            source=runner.name,
            operation="generate_bull_bear_cases",
            context={"company_count": "1"},
        )
        runner.gemini_service.upsert_bull_bear.assert_not_called()

    def test_reports_persistence_failure_per_company(self, runner):
        report_date = date.today() - timedelta(days=5)
        quarter = make_gemini_quarter(
            report_date_this_quarter=report_date.isoformat(),
            reported_revenues="1000",
        )
        runner.gemini_service.get_companies.return_value = {
            "AAPL": make_gemini_company(
                ticker="AAPL",
                quarters={"26Q3": quarter},
            ),
            "MSFT": make_gemini_company(
                ticker="MSFT",
                quarters={"26Q3": quarter},
            ),
        }
        runner.finnhub_service.get_companies.return_value = {}
        aapl_case = CompanyBullBear(
            ticker="AAPL",
            bull=[{"point": "p", "reasoning": "r"}],
            bear=[{"point": "p", "reasoning": "r"}],
        )
        msft_case = CompanyBullBear(
            ticker="MSFT",
            bull=[{"point": "p", "reasoning": "r"}],
            bear=[{"point": "p", "reasoning": "r"}],
        )
        runner.gemini.get_bull_bear_cases.return_value = [
            aapl_case,
            msft_case,
        ]
        error = RuntimeError("Firebase unavailable")
        runner.gemini_service.upsert_bull_bear.side_effect = [error, "ok"]

        runner._generate_bull_bear_cases({"AAPL": None, "MSFT": None})

        assert runner.gemini_service.upsert_bull_bear.call_count == 2
        runner.errors.report.assert_called_once_with(
            error,
            logger=runner.log,
            source=runner.name,
            operation="persist_bull_bear_case",
            context={"ticker": "AAPL"},
        )
