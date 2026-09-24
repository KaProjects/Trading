import logging
from datetime import date as Date
from decimal import Decimal
from typing import Annotated

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    ValidationError,
    ValidationInfo,
    field_validator,
    model_validator,
)

from domain_types import QuarterId, Ticker

logger = logging.getLogger(__name__)
TARGET_REPORT_OVERVIEW_MAX_LENGTH = 1000
TARGET_REPORT_TAKEAWAY_MAX_LENGTH = 500
TARGET_REPORT_TAKEAWAYS_MAX_COUNT = 4
BULL_BEAR_POINTS_MAX_COUNT = 3
BULL_BEAR_POINT_MAX_LENGTH = 120
BULL_BEAR_REASONING_MAX_LENGTH = 600

EndingMonth = Annotated[
    str,
    StringConstraints(pattern=r"^\d{2}-(0[1-9]|1[0-2])$"),
]
CurrencySymbol = Annotated[
    str,
    StringConstraints(min_length=1, max_length=4, strip_whitespace=True),
]
QuarterName = Annotated[
    str,
    StringConstraints(min_length=1, strip_whitespace=True),
]
InstitutionName = Annotated[
    str,
    StringConstraints(min_length=1, max_length=200, strip_whitespace=True),
]
Rating = Annotated[
    str,
    StringConstraints(min_length=1, max_length=100, strip_whitespace=True),
]
Source = Annotated[
    str,
    StringConstraints(min_length=1, max_length=1024, strip_whitespace=True),
]
InitialCompanyError = Annotated[
    str,
    StringConstraints(min_length=1, strip_whitespace=True),
]
ReportOverview = Annotated[
    str,
    StringConstraints(
        min_length=1,
        max_length=TARGET_REPORT_OVERVIEW_MAX_LENGTH,
        strip_whitespace=True,
    ),
]
ReportTakeaway = Annotated[
    str,
    StringConstraints(
        min_length=1,
        max_length=TARGET_REPORT_TAKEAWAY_MAX_LENGTH,
        strip_whitespace=True,
    ),
]


def _truncate_with_dots(value: str, max_length: int) -> str:
    return value[:max_length - 3] + "..."


def _target_from_validation(info: ValidationInfo) -> str:
    if isinstance(info.context, dict):
        target = info.context.get("target")
        if isinstance(target, str):
            return target
    return "unknown target"


def _dropped_items_sink(info: ValidationInfo | None) -> list | None:
    if info is None or not isinstance(info.context, dict):
        return None
    sink = info.context.get("dropped_items")
    return sink if isinstance(sink, list) else None


def _drop_invalid_items(
    data: object,
    *,
    list_field: str,
    item_model: type[BaseModel],
    info: ValidationInfo | None = None,
) -> object:
    # One hallucinated or malformed row must never fail an entire batched
    # Gemini response and lose every other, valid item in it.
    if not isinstance(data, dict):
        return data
    raw_items = data.get(list_field)
    if not isinstance(raw_items, list):
        return data

    dropped_sink = _dropped_items_sink(info)
    kept = []
    for index, raw_item in enumerate(raw_items):
        try:
            item_model.model_validate(raw_item)
        except ValidationError as exception:
            logger.warning(
                "Dropping invalid %s entry at index %d: %s",
                item_model.__name__,
                index,
                exception,
            )
            if dropped_sink is not None:
                dropped_sink.append(
                    (item_model.__name__, index, str(exception))
                )
            continue
        kept.append(raw_item)
    return {**data, list_field: kept}


class Info(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticker: Ticker = Field(description="ticker of the company")
    currency: CurrencySymbol = Field(
        default="$",
        description=(
            "Symbol of the currency used in the company's official financial "
            "statements, for example $, €, or £."
        ),
    )
    last_update: Date = Field(description="date of this data creation")
    current_quarter_id: QuarterId = Field(description="in format YYQX")


class InitialInfo(Info):
    currency: CurrencySymbol = Field(
        description=(
            "Symbol of the original reporting currency used in the company's "
            "official financial statements, for example $, €, or £."
        ),
    )


class Quarter(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: QuarterName = Field(
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
        description=(
            "Reported earnings per share in the company's original reporting "
            "currency per share."
        ),
    )
    reported_revenues: Decimal | None = Field(
        default=None,
        description=(
            "Reported revenue in millions of the company's original reporting "
            "currency; for example, 16130 means 16.13 billion."
        ),
    )
    reported_gross_profit: Decimal | None = Field(
        default=None,
        description=(
            "Reported gross profit in millions of the company's original "
            "reporting currency."
        ),
    )
    reported_operating_income: Decimal | None = Field(
        default=None,
        description=(
            "Reported operating income in millions of the company's original "
            "reporting currency."
        ),
    )
    reported_net_income: Decimal | None = Field(
        default=None,
        description=(
            "Reported net income in millions of the company's original "
            "reporting currency."
        ),
    )
    reported_capex: Decimal | None = Field(
        default=None,
        description=(
            "Capital expenditures for the individual fiscal quarter in "
            "millions of the company's original reporting currency, stored "
            "as a positive cash outflow amount."
        ),
    )
    reported_free_cash_flow: Decimal | None = Field(
        default=None,
        description=(
            "Free cash flow for the individual fiscal quarter in millions "
            "of the company's original reporting currency."
        ),
    )
    reported_div: Decimal | None = Field(
        default=None,
        description=(
            "Reported total dividends in millions of the company's original "
            "reporting currency."
        ),
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
            "Minimum stock price in the ticker's trading currency per share "
            "between the previous and current report dates, excluding both "
            "edge dates."
        ),
    )
    price_max: Decimal | None = Field(
        default=None,
        description=(
            "Maximum stock price in the ticker's trading currency per share "
            "between the previous and current report dates, excluding both "
            "edge dates."
        ),
    )

    @field_validator(
        "report_date_this_quarter",
        "reported_eps",
        "reported_revenues",
        "reported_gross_profit",
        "reported_operating_income",
        "reported_net_income",
        "reported_capex",
        "reported_free_cash_flow",
        "reported_div",
        "reported_shares",
        "price_min",
        "price_max",
        mode="before",
    )
    @classmethod
    def empty_string_is_unavailable(cls, value):
        return None if value == "" else value


class TargetReport(BaseModel):
    model_config = ConfigDict(extra="forbid")

    overview: ReportOverview = Field(
        description=(
            "Concise overview of the research relevant to this institutional "
            "price-target action."
        ),
    )
    key_takeaways: list[ReportTakeaway] = Field(
        min_length=1,
        max_length=TARGET_REPORT_TAKEAWAYS_MAX_COUNT,
        description=(
            "Most important facts and conclusions, with one self-contained "
            "message per list item."
        ),
    )

    @field_validator("overview", mode="before")
    @classmethod
    def truncate_overview(cls, value, info: ValidationInfo):
        if not isinstance(value, str):
            return value
        value = value.strip()
        if len(value) <= TARGET_REPORT_OVERVIEW_MAX_LENGTH:
            return value
        logger.warning(
            "Target report overview exceeds character limit for %s "
            "(actual=%d, limit=%d); truncating",
            _target_from_validation(info),
            len(value),
            TARGET_REPORT_OVERVIEW_MAX_LENGTH,
        )
        return _truncate_with_dots(
            value,
            TARGET_REPORT_OVERVIEW_MAX_LENGTH,
        )

    @field_validator("key_takeaways", mode="before")
    @classmethod
    def normalize_key_takeaways(cls, value, info: ValidationInfo):
        if not isinstance(value, list):
            return value

        target = _target_from_validation(info)
        if len(value) > TARGET_REPORT_TAKEAWAYS_MAX_COUNT:
            logger.warning(
                "Target report takeaway count exceeds limit for %s "
                "(actual=%d, limit=%d); omitting %d excess takeaways",
                target,
                len(value),
                TARGET_REPORT_TAKEAWAYS_MAX_COUNT,
                len(value) - TARGET_REPORT_TAKEAWAYS_MAX_COUNT,
            )

        normalized = []
        for index, takeaway in enumerate(
            value[:TARGET_REPORT_TAKEAWAYS_MAX_COUNT],
            start=1,
        ):
            if not isinstance(takeaway, str):
                normalized.append(takeaway)
                continue
            takeaway = takeaway.strip()
            if len(takeaway) > TARGET_REPORT_TAKEAWAY_MAX_LENGTH:
                logger.warning(
                    "Target report takeaway %d exceeds character limit for "
                    "%s (actual=%d, limit=%d); truncating",
                    index,
                    target,
                    len(takeaway),
                    TARGET_REPORT_TAKEAWAY_MAX_LENGTH,
                )
                takeaway = _truncate_with_dots(
                    takeaway,
                    TARGET_REPORT_TAKEAWAY_MAX_LENGTH,
                )
            normalized.append(takeaway)
        return normalized


class BullBearPoint(BaseModel):
    model_config = ConfigDict(extra="forbid")

    point: str = Field(
        max_length=BULL_BEAR_POINT_MAX_LENGTH,
        description=(
            "The business driver or risk itself, at most "
            f"{BULL_BEAR_POINT_MAX_LENGTH} characters."
        ),
    )
    reasoning: str = Field(
        max_length=BULL_BEAR_REASONING_MAX_LENGTH,
        description=(
            "Why it matters for this company in this period, grounded in "
            f"the provided research, at most {BULL_BEAR_REASONING_MAX_LENGTH} "
            "characters."
        ),
    )


class BullBearContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticker: Ticker
    period: QuarterId = Field(
        description="Fiscal quarter identifier in YYQX format."
    )
    research: str = Field(
        description="Pre-formatted research context for this company."
    )


class CompanyBullBear(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticker: Ticker = Field(
        description="Exact ticker from the requested company list."
    )
    bull: list[BullBearPoint] = Field(
        max_length=BULL_BEAR_POINTS_MAX_COUNT,
        description=(
            f"Zero to {BULL_BEAR_POINTS_MAX_COUNT} points supporting the "
            "company, strongest first; empty when the research does not "
            "honestly support any."
        ),
    )
    bear: list[BullBearPoint] = Field(
        max_length=BULL_BEAR_POINTS_MAX_COUNT,
        description=(
            f"Zero to {BULL_BEAR_POINTS_MAX_COUNT} points working against "
            "the company, strongest first; empty when the research does "
            "not honestly support any."
        ),
    )


class CompanyBullBearCases(BaseModel):
    model_config = ConfigDict(extra="forbid")

    cases: list[CompanyBullBear] = Field(
        description=(
            "One bull/bear case per requested company, in the same order "
            "they were requested."
        ),
    )

    @model_validator(mode="before")
    @classmethod
    def drop_invalid_cases(cls, data, info: ValidationInfo):
        return _drop_invalid_items(
            data,
            list_field="cases",
            item_model=CompanyBullBear,
            info=info,
        )


class TargetFields(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    institution: InstitutionName = Field(
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

    @field_validator("price", mode="before")
    @classmethod
    def strip_thousands_separators(cls, value):
        if isinstance(value, str):
            return value.replace(",", "")
        return value


class CompanyTarget(TargetFields):
    report: TargetReport | None = Field(
        default=None,
        description=(
            "Additional Gemini research for targets issued by trusted "
            "institutions, or null when no report was requested."
        ),
    )


class InstitutionRatingDimension(BaseModel):
    model_config = ConfigDict(extra="forbid")

    score: str
    description: str


class InstitutionRating(BaseModel):
    model_config = ConfigDict(extra="forbid")

    institutional_weight: InstitutionRatingDimension
    media_shock_value: InstitutionRatingDimension


class InstitutionResolution(BaseModel):
    model_config = ConfigDict(extra="forbid")

    institution: InstitutionName = Field(
        description=(
            "Exact candidate institution name, copied verbatim from the "
            "NEW INSTITUTIONS list."
        ),
    )
    is_alias: bool = Field(
        description=(
            "True if this candidate refers to the same real institution as "
            "one of the EXISTING INSTITUTIONS under a different name, "
            "spelling, abbreviation, or legal-entity variant; false if it "
            "is a genuinely distinct institution not already tracked."
        ),
    )
    alias_of: InstitutionName | None = Field(
        default=None,
        description=(
            "When is_alias is true, the exact matching name copied "
            "verbatim from EXISTING INSTITUTIONS; null when is_alias is "
            "false."
        ),
    )
    institutional_weight: InstitutionRatingDimension | None = Field(
        default=None,
        description=(
            "0-10 Institutional Execution Weight (\"Wall Street Tier\") "
            "score and one-sentence explanation; required when is_alias is "
            "false, null when is_alias is true."
        ),
    )
    media_shock_value: InstitutionRatingDimension | None = Field(
        default=None,
        description=(
            "0-10 Media & Shock Value (\"Headline Tier\") score and "
            "one-sentence explanation; required when is_alias is false, "
            "null when is_alias is true."
        ),
    )


class InstitutionResolutions(BaseModel):
    model_config = ConfigDict(extra="forbid")

    resolutions: list[InstitutionResolution] = Field(
        description=(
            "One resolution per name in NEW INSTITUTIONS, in the same "
            "order they were requested."
        ),
    )

    @model_validator(mode="before")
    @classmethod
    def drop_invalid_resolutions(cls, data, info: ValidationInfo):
        return _drop_invalid_items(
            data,
            list_field="resolutions",
            item_model=InstitutionResolution,
            info=info,
        )


class InstitutionRecord(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: InstitutionName
    aliases: dict[str, InstitutionName] = Field(default_factory=dict)
    enabled: bool = True
    trusted: bool = False
    # A handful of institutions still carry the old free-text rating this
    # replaces; accept both shapes so a stale record doesn't break parsing
    # before this backfill has run everywhere.
    rating: InstitutionRating | str | None = None


class TargetCandidate(TargetFields):
    # No `report` field: only a `Target`, mapped in after institution trust
    # is decided, may carry one.
    ticker: Ticker = Field(
        description=(
            "Exact ticker from the requested ticker list to which this price "
            "target applies."
        ),
    )


class TargetCandidates(BaseModel):
    model_config = ConfigDict(extra="forbid")

    targets: list[TargetCandidate] = Field(
        description=(
            "All qualifying institutional price targets for the requested "
            "tickers and date interval; an empty list is valid."
        ),
    )

    @model_validator(mode="before")
    @classmethod
    def drop_invalid_targets(cls, data, info: ValidationInfo):
        return _drop_invalid_items(
            data,
            list_field="targets",
            item_model=TargetCandidate,
            info=info,
        )


class Target(CompanyTarget):
    ticker: Ticker = Field(
        description=(
            "Exact ticker from the requested ticker list to which this price "
            "target applies."
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


class InitialQuarter(Quarter):
    report_date_this_quarter: Date = Field(
        description=(
            "Actual or expected report date for this fiscal quarter, in "
            "YYYY-MM-DD format."
        ),
    )
    reported_eps: Decimal | None = Field(
        description=(
            "Reported earnings per share in the company's original reporting "
            "currency per share, or null."
        )
    )
    reported_revenues: Decimal | None = Field(
        description=(
            "Reported revenue in millions of the company's original reporting "
            "currency, or null."
        )
    )
    reported_gross_profit: Decimal | None = Field(
        description=(
            "Reported gross profit in millions of the company's original "
            "reporting currency, or null."
        )
    )
    reported_operating_income: Decimal | None = Field(
        description=(
            "Reported operating income in millions of the company's original "
            "reporting currency, or null."
        )
    )
    reported_net_income: Decimal | None = Field(
        description=(
            "Reported net income in millions of the company's original "
            "reporting currency, or null."
        )
    )
    reported_capex: Decimal | None = Field(
        description=(
            "Capital expenditures for the individual fiscal quarter in "
            "millions of the company's original reporting currency, stored "
            "as a positive cash outflow amount, or null."
        )
    )
    reported_free_cash_flow: Decimal | None = Field(
        description=(
            "Free cash flow for the individual fiscal quarter in millions "
            "of the company's original reporting currency, or null."
        )
    )
    reported_div: Decimal | None = Field(
        description=(
            "Reported total dividends in millions of the company's original "
            "reporting currency, or null."
        )
    )
    reported_shares: Decimal | None = Field(
        description="Reported number of shares in millions of shares, or null."
    )
    price_min: Decimal | None = Field(
        description=(
            "Minimum stock price in the ticker's trading currency per share "
            "between report dates, or null."
        )
    )
    price_max: Decimal | None = Field(
        description=(
            "Maximum stock price in the ticker's trading currency per share "
            "between report dates, or null."
        )
    )


class InitialCompanyResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    info: InitialInfo
    quarters: list[InitialQuarter] = Field(
        min_length=5,
        max_length=5,
        description=(
            "Exactly five fiscal quarters: the current unreported quarter "
            "followed by its four immediately preceding quarters."
        ),
    )
    errors: list[InitialCompanyError] = Field(
        description=(
            "Concise retrieval errors, uncertainties, conflicting sources, "
            "or unavailable optional values encountered while constructing "
            "the company data; empty when no issues occurred."
        ),
    )


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

    @model_validator(mode="before")
    @classmethod
    def drop_invalid_report_dates(cls, data, info: ValidationInfo):
        return _drop_invalid_items(
            data,
            list_field="report_dates",
            item_model=ReportDate,
            info=info,
        )

    @model_validator(mode="after")
    def identities_are_unique(self):
        identities = [
            (report.ticker, report.quarter)
            for report in self.report_dates
        ]
        if len(identities) != len(set(identities)):
            raise ValueError("Report-date identities must be unique")
        return self
