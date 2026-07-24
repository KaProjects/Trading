from datetime import date as Date
from decimal import Decimal
from typing import Annotated

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    field_validator,
    model_validator,
)

from domain_types import QuarterId, Ticker

EndingMonth = Annotated[
    str,
    StringConstraints(pattern=r"^\d{2}-(0[1-9]|1[0-2])$"),
]
Institution = Annotated[
    str,
    StringConstraints(min_length=1, max_length=200, strip_whitespace=True),
]
Rating = Annotated[
    str,
    StringConstraints(min_length=1, max_length=100, strip_whitespace=True),
]
Source = Annotated[
    str,
    StringConstraints(min_length=1, max_length=2048, strip_whitespace=True),
]


class Info(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticker: Ticker = Field(description="ticker of the company")
    last_update: Date = Field(description="date of this data creation")
    current_quarter_id: QuarterId = Field(description="in format YYQX")


class Quarter(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(
        description="Human-readable fiscal quarter name, for example Q2 2026."
    )
    id: QuarterId = Field(
        description="Fiscal quarter identifier in YYQX format."
    )
    ending_month: EndingMonth = Field(
        description="Fiscal quarter ending month in YY-MM format."
    )
    report_date_previous_quarter: Date = Field(
        description=(
            "Date on which the previous fiscal quarter was reported, in "
            "YYYY-MM-DD format."
        )
    )
    report_date_this_quarter: Date | None = Field(
        default=None,
        description=(
            "Actual or expected report date for this fiscal quarter, in "
            "YYYY-MM-DD format."
        ),
    )
    reported_eps: Decimal | None = Field(
        default=None,
        description="Reported earnings per share in USD per share.",
    )
    reported_revenues: Decimal | None = Field(
        default=None,
        description=(
            "Reported revenue in millions of USD; for example, 16130 means "
            "USD 16.13 billion."
        ),
    )
    reported_gross_profit: Decimal | None = Field(
        default=None,
        description="Reported gross profit in millions of USD.",
    )
    reported_operating_income: Decimal | None = Field(
        default=None,
        description="Reported operating income in millions of USD.",
    )
    reported_net_income: Decimal | None = Field(
        default=None,
        description="Reported net income in millions of USD.",
    )
    reported_div: Decimal | None = Field(
        default=None,
        description="Reported total dividends in millions of USD.",
    )
    reported_shares: Decimal | None = Field(
        default=None,
        description=(
            "Reported number of shares in millions of shares; for example, "
            "5104 means 5.104 billion shares."
        ),
    )
    price_min: Decimal | None = Field(
        default=None,
        description=(
            "Minimum stock price in USD per share between the previous and "
            "current report dates, excluding both edge dates."
        ),
    )
    price_max: Decimal | None = Field(
        default=None,
        description=(
            "Maximum stock price in USD per share between the previous and "
            "current report dates, excluding both edge dates."
        ),
    )

    @field_validator(
        "report_date_this_quarter",
        "reported_eps",
        "reported_revenues",
        "reported_gross_profit",
        "reported_operating_income",
        "reported_net_income",
        "reported_div",
        "reported_shares",
        "price_min",
        "price_max",
        mode="before",
    )
    @classmethod
    def empty_string_is_unavailable(cls, value):
        return None if value == "" else value


class CompanyTarget(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    institution: Institution = Field(
        description=(
            "Canonical name of the important institutional equity research "
            "provider that issued the price target."
        ),
    )
    date: Date = Field(
        description=(
            "Date on which the institution announced the price-target action, "
            "in YYYY-MM-DD format."
        ),
    )
    price: Decimal = Field(
        gt=0,
        description=(
            "Newly announced target price in USD, excluding previous targets, "
            "consensus targets, and the current market price."
        ),
    )
    rating: Rating | None = Field(
        default=None,
        description=(
            "Current analyst rating exactly as stated by the source, or null "
            "when the source does not state a rating."
        ),
    )
    source: Source = Field(
        description=(
            "Direct public URL supporting the target, or the source hostname "
            "when a direct URL is unavailable."
        ),
    )


class Target(CompanyTarget):
    ticker: Ticker = Field(
        description=(
            "Exact ticker from the requested ticker list to which this price "
            "target applies."
        ),
    )


class Targets(BaseModel):
    model_config = ConfigDict(extra="forbid")

    targets: list[Target] = Field(
        description=(
            "All qualifying institutional price targets for the requested "
            "tickers and date interval; an empty list is valid."
        ),
    )


class Company(BaseModel):
    model_config = ConfigDict(extra="forbid")

    info: Info
    quarters: dict[QuarterId, Quarter]
    targets: dict[str, CompanyTarget] = Field(default_factory=dict)

    @model_validator(mode="after")
    def quarter_keys_match_ids(self):
        mismatched = [
            key
            for key, quarter in self.quarters.items()
            if key != quarter.id
        ]
        if mismatched:
            raise ValueError(f"Quarter keys do not match their IDs: {mismatched}")
        return self


class ReportDate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticker: Ticker = Field(description="ticker of the company")
    quarter: QuarterId = Field(description="ID of the current quarter")
    report_date: Date | None = Field(
        description="quarterly report date, if available"
    )

    @field_validator("report_date", mode="before")
    @classmethod
    def empty_report_date_is_unavailable(cls, value):
        return None if value == "" else value


class ReportDates(BaseModel):
    model_config = ConfigDict(extra="forbid")

    report_dates: list[ReportDate]

    @model_validator(mode="after")
    def identities_are_unique(self):
        identities = [
            (report.ticker, report.quarter)
            for report in self.report_dates
        ]
        if len(identities) != len(set(identities)):
            raise ValueError("Report-date identities must be unique")
        return self
