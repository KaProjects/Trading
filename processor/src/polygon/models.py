from datetime import datetime
from typing import Annotated

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    RootModel,
    StringConstraints,
    model_validator,
)

from domain_types import Ticker

SentimentTakeaway = Annotated[
    str,
    StringConstraints(min_length=1, max_length=500, strip_whitespace=True),
]
SentimentLabel = Annotated[
    str,
    StringConstraints(min_length=1, strip_whitespace=True),
]
SentimentCount = Annotated[int, Field(gt=0)]
MISSING_SENTIMENT_LABEL = "missing"


class NewsPublisher(BaseModel):
    model_config = ConfigDict(extra="ignore")

    name: str
    homepage_url: str | None = None
    logo_url: str | None = None
    favicon_url: str | None = None


class NewsInsight(BaseModel):
    model_config = ConfigDict(extra="ignore")

    ticker: str
    sentiment: str | None = None
    sentiment_reasoning: str | None = None


class CompanyNewsInsight(NewsInsight):
    model_config = ConfigDict(extra="forbid")

    article_id: str = Field(min_length=1)


class NewsArticle(BaseModel):
    model_config = ConfigDict(extra="ignore")

    id: str
    publisher: NewsPublisher
    title: str
    author: str | None = None
    published_utc: datetime
    article_url: str
    tickers: list[str] = Field(default_factory=list)
    image_url: str | None = None
    amp_url: str | None = None
    description: str | None = None
    keywords: list[str] = Field(default_factory=list)
    insights: list[NewsInsight] = Field(default_factory=list)


class NewsResponse(BaseModel):
    model_config = ConfigDict(extra="ignore")

    results: list[NewsArticle] = Field(default_factory=list)
    count: int | None = None
    status: str | None = None
    request_id: str | None = None
    next_url: str | None = None


class CompanyNews(BaseModel):
    model_config = ConfigDict(extra="forbid")

    insights: list[CompanyNewsInsight] = Field(default_factory=list)


class CompanyInsights(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticker: Ticker
    insights: list[CompanyNewsInsight] = Field(default_factory=list)

    @model_validator(mode="after")
    def insight_articles_are_unique_and_match_company(self):
        article_ids = [insight.article_id for insight in self.insights]
        if len(article_ids) != len(set(article_ids)):
            raise ValueError("company insight article ids must be unique")
        if any(
            insight.ticker.casefold() != self.ticker.casefold()
            for insight in self.insights
        ):
            raise ValueError("company insight tickers must match company")
        return self


class SentimentStatistics(BaseModel):
    model_config = ConfigDict(extra="allow")

    __pydantic_extra__: dict[SentimentLabel, SentimentCount] = Field(
        init=False
    )

    total: int = Field(ge=0, description="Total number of input insights.")

    @property
    def counts(self) -> dict[str, int]:
        return dict(self.__pydantic_extra__ or {})

    @model_validator(mode="after")
    def categories_sum_to_total(self):
        if sum(self.counts.values()) != self.total:
            raise ValueError("sentiment counts must equal total")
        return self

    @classmethod
    def from_insights(
        cls,
        insights: list[NewsInsight],
    ) -> "SentimentStatistics":
        counts: dict[str, int] = {}
        for insight in insights:
            label = (
                insight.sentiment.strip().casefold()
                if insight.sentiment is not None
                else ""
            )
            label = label or MISSING_SENTIMENT_LABEL
            counts[label] = counts.get(label, 0) + 1
        return cls(
            total=len(insights),
            **dict(sorted(counts.items())),
        )


class CompanySentimentAnalysis(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticker: Ticker
    statistics: SentimentStatistics
    key_takeaways: list[SentimentTakeaway] = Field(
        default_factory=list,
        max_length=5,
        description=(
            "Concise synthesis of the recurring events, drivers, risks, and "
            "conflicting signals in the supplied sentiment reasoning."
        ),
    )

    @model_validator(mode="after")
    def empty_input_has_no_takeaways(self):
        if self.statistics.total == 0 and self.key_takeaways:
            raise ValueError("zero insights must produce no key takeaways")
        return self


class NewsSentimentRecord(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sentiment: dict[SentimentLabel, SentimentCount]
    key_takeaways: list[SentimentTakeaway] = Field(
        default_factory=list,
        max_length=5,
    )

    @classmethod
    def from_analysis(
        cls,
        analysis: CompanySentimentAnalysis,
    ) -> "NewsSentimentRecord":
        return cls(
            sentiment=analysis.statistics.counts,
            key_takeaways=analysis.key_takeaways,
        )


class CompanyNewsHistory(RootModel[dict[str, NewsSentimentRecord]]):
    pass


class CompanySentimentSummary(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticker: Ticker
    key_takeaways: list[SentimentTakeaway] = Field(
        default_factory=list,
        max_length=5,
        description=(
            "Concise synthesis of the recurring events, drivers, risks, and "
            "conflicting signals in the supplied sentiment reasoning."
        ),
    )


class CompanySentimentSummaries(BaseModel):
    model_config = ConfigDict(extra="forbid")

    companies: list[CompanySentimentSummary]

    @model_validator(mode="after")
    def company_tickers_are_unique(self):
        tickers = [company.ticker for company in self.companies]
        if len(tickers) != len(set(tickers)):
            raise ValueError("company sentiment tickers must be unique")
        return self
