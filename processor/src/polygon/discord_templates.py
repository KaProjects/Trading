from collections.abc import Mapping

from polygon.models import CompanySentimentAnalysis, SentimentStatistics

DISCORD_DESCRIPTION_PAGE_LENGTH = 2500
REPORTER_USERNAME = "Polygon News Reporter"
SUMMARY_TITLE = "📰 Polygon news coverage"
FIREBASE_TITLE = "🔥 Firebase companies"
UNMATCHED_TITLE = "🔎 Companies missing from Firebase"
UNMATCHED_FOOTER = "At least 5 articles; aliases and share classes excluded"
SENTIMENT_TITLE = "📰 Weekly News Sentiment Analysis"


def news_coverage_reports(
    *,
    polygon_article_count: int,
    firebase_counts: Mapping[str, int],
    total_firebase_article_count: int,
    unmatched_counts: Mapping[str, int],
    total_unmatched_article_count: int,
) -> list[dict[str, object]]:
    firebase_descriptions = _count_descriptions(
        firebase_counts,
        empty_message="No Firebase companies found.",
    )
    unmatched_descriptions = _count_descriptions(
        unmatched_counts,
        empty_message="No unmatched companies passed the filter.",
    )
    firebase_footer = (
        f"{total_firebase_article_count} total "
        f"{_articles(total_firebase_article_count)} across "
        f"{len(firebase_counts)} Firebase "
        f"{_companies(len(firebase_counts))}"
    )
    unmatched_footer = (
        f"{total_unmatched_article_count} total "
        f"{_articles(total_unmatched_article_count)} before filtering | "
        f"{UNMATCHED_FOOTER}"
    )

    reports = [{
        "username": REPORTER_USERNAME,
        "embeds": [
            {
                "title": SUMMARY_TITLE,
                "description": (
                    f"**{polygon_article_count} "
                    f"{_articles(polygon_article_count)} returned**"
                ),
                "color": 0x3498DB,
            },
            _coverage_embed(
                title=FIREBASE_TITLE,
                description=firebase_descriptions[0],
                color=0x2ECC71,
                footer=_page_footer(
                    firebase_footer,
                    page=1,
                    page_count=len(firebase_descriptions),
                ),
            ),
            _coverage_embed(
                title=UNMATCHED_TITLE,
                description=unmatched_descriptions[0],
                color=0xF39C12,
                footer=_page_footer(
                    unmatched_footer,
                    page=1,
                    page_count=len(unmatched_descriptions),
                ),
            ),
        ],
    }]
    reports.extend(_continuation_reports(
        title=FIREBASE_TITLE,
        descriptions=firebase_descriptions,
        color=0x2ECC71,
        footer=firebase_footer,
    ))
    reports.extend(_continuation_reports(
        title=UNMATCHED_TITLE,
        descriptions=unmatched_descriptions,
        color=0xF39C12,
        footer=unmatched_footer,
    ))
    return reports


def ticker_news_sentiment(
    analysis: CompanySentimentAnalysis,
) -> dict[str, object]:
    return {
        "embeds": [_news_sentiment_embed(analysis, include_ticker=False)],
    }


def eventlog_news_sentiment(
    analysis: CompanySentimentAnalysis,
) -> dict[str, object]:
    return {
        "username": REPORTER_USERNAME,
        "embeds": [_news_sentiment_embed(analysis, include_ticker=True)],
    }


def _news_sentiment_embed(
    analysis: CompanySentimentAnalysis,
    *,
    include_ticker: bool,
) -> dict[str, object]:
    title = SENTIMENT_TITLE
    if include_ticker:
        title = f"📰 {analysis.ticker} | Weekly News Sentiment Analysis"
    takeaways = (
        "\n".join(f"- {takeaway}" for takeaway in analysis.key_takeaways)
        or "_No key takeaways returned._"
    )
    return {
        "title": title,
        "description": (
            "**Statistics**\n"
            f"{_sentiment_statistics_line(analysis.statistics)}\n\n"
            "**Key takeaways**\n"
            f"{takeaways}"
        ),
        "color": 0x3498DB,
    }


def _sentiment_statistics_line(statistics: SentimentStatistics) -> str:
    preferred_order = {"positive": 0, "neutral": 1, "negative": 2}
    labels = sorted(
        statistics.counts,
        key=lambda label: (preferred_order.get(label, 3), label),
    )
    values = [f"Total: **{statistics.total}**"]
    values.extend(
        f"{label.replace('_', ' ').title()}: "
        f"**{statistics.counts[label]}**"
        for label in labels
    )
    return " | ".join(values)


def _continuation_reports(
    *,
    title: str,
    descriptions: list[str],
    color: int,
    footer: str,
) -> list[dict[str, object]]:
    page_count = len(descriptions)
    return [
        {
            "username": REPORTER_USERNAME,
            "embeds": [_coverage_embed(
                title=title,
                description=description,
                color=color,
                footer=_page_footer(
                    footer,
                    page=page,
                    page_count=page_count,
                ),
            )],
        }
        for page, description in enumerate(descriptions[1:], start=2)
    ]


def _coverage_embed(
    *,
    title: str,
    description: str,
    color: int,
    footer: str,
) -> dict[str, object]:
    return {
        "title": title,
        "description": description,
        "color": color,
        "footer": {"text": footer},
    }


def _page_footer(text: str, *, page: int, page_count: int) -> str:
    if page_count == 1:
        return text
    return f"{text} | {page}/{page_count}"


def _count_descriptions(
    counts: Mapping[str, int],
    *,
    empty_message: str,
) -> list[str]:
    lines = [
        f"`{ticker}`: **{count} {_articles(count)}**"
        for ticker, count in counts.items()
    ]
    return _chunk_lines(lines) or [empty_message]


def _articles(count: int) -> str:
    return "article" if count == 1 else "articles"


def _companies(count: int) -> str:
    return "company" if count == 1 else "companies"


def _chunk_lines(lines: list[str]) -> list[str]:
    chunks: list[str] = []
    current: list[str] = []
    current_length = 0

    for line in lines:
        added_length = len(line) + (1 if current else 0)
        if current and (
            current_length + added_length
            > DISCORD_DESCRIPTION_PAGE_LENGTH
        ):
            chunks.append("\n".join(current))
            current = []
            current_length = 0
            added_length = len(line)

        current.append(line)
        current_length += added_length

    if current:
        chunks.append("\n".join(current))
    return chunks
