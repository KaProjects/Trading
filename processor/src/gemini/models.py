from datetime import date
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


class Info(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticker: Ticker = Field(description="ticker of the company")
    last_update: date = Field(description="date of this data creation")
    current_quarter_id: QuarterId = Field(description="in format YYQX")


class Quarter(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(description="Name of the quarter.")
    id: QuarterId = Field(description="in format YYQX")
    ending_month: EndingMonth = Field(description="end month in format YY-MM")
    report_date_previous_quarter: date
    report_date_this_quarter: date | None = None
    reported_eps: Decimal | None = None
    reported_revenues: Decimal | None = None
    reported_gross_profit: Decimal | None = None
    reported_operating_income: Decimal | None = None
    reported_net_income: Decimal | None = None
    reported_div: Decimal | None = None
    reported_shares: Decimal | None = None
    price_min: Decimal | None = None
    price_max: Decimal | None = None

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


class Company(BaseModel):
    model_config = ConfigDict(extra="forbid")

    info: Info
    quarters: dict[QuarterId, Quarter]

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
    report_date: date | None = Field(
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
