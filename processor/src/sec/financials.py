import calendar
import logging
from collections.abc import Sequence
from dataclasses import dataclass, field
from datetime import date, timedelta
from decimal import Decimal

logger = logging.getLogger(__name__)
FILINGS = {"10-Q", "10-K"}
MILLION = Decimal(1_000_000)
# 52/53-week fiscal calendars end a few days off the month end.
END_TOLERANCE_DAYS = 8
QUARTER_DAYS = (80, 100)
YEAR_DAYS = (340, 380)
NINE_MONTH_DAYS = (250, 290)

# Candidate us-gaap tags per stored field, best first. Which one a company
# uses changes over the years, so the first tag with a usable value for the
# period is taken - not the first tag the company ever used.
REVENUE = (
    "Revenues",
    "RevenueFromContractWithCustomerExcludingAssessedTax",
    "RevenueFromContractWithCustomerIncludingAssessedTax",
    "SalesRevenueNet",
    "RevenuesNetOfInterestExpense",
)
FLOW_FIELDS = {
    "reported_revenues": (REVENUE, "USD", MILLION),
    "reported_gross_profit": (("GrossProfit",), "USD", MILLION),
    "reported_operating_income": (("OperatingIncomeLoss",), "USD", MILLION),
    "reported_net_income": (("NetIncomeLoss", "ProfitLoss"), "USD", MILLION),
    "reported_capex": (
        (
            "PaymentsToAcquirePropertyPlantAndEquipment",
            "PaymentsToAcquireProductiveAssets",
            "PaymentsForCapitalImprovements",
        ),
        "USD",
        MILLION,
    ),
    # Total dividends in millions, as the stored field is.
    "reported_div": (
        (
            "PaymentsOfDividendsCommonStock",
            "PaymentsOfDividends",
            "DividendsCommonStockCash",
        ),
        "USD",
        MILLION,
    ),
}
# A negative figure here comes from subtracting two periods that were
# reported on different bases (a restatement, discontinued operations).
NON_NEGATIVE_FIELDS = frozenset({
    "reported_revenues",
    "reported_capex",
    "reported_div",
})
# An estimated share count further than this from the quarters around it
# is a sign the year was reported on another basis (a stock split).
MAX_SHARE_ESTIMATE_DEVIATION = Decimal("0.35")
OPERATING_CASH_FLOW = ("NetCashProvidedByUsedInOperatingActivities",)
DILUTED_EPS = ("EarningsPerShareDiluted",)
DILUTED_SHARES = ("WeightedAverageNumberOfDilutedSharesOutstanding",)

EPS_FIELD = "reported_eps"
SHARES_FIELD = "reported_shares"
FREE_CASH_FLOW_FIELD = "reported_free_cash_flow"
ESTIMATED_FIELDS = (EPS_FIELD, SHARES_FIELD)


@dataclass(frozen=True)
class Period:
    start: date
    end: date
    value: Decimal

    @property
    def days(self) -> int:
        return (self.end - self.start).days


@dataclass
class QuarterValues:
    values: dict[str, Decimal] = field(default_factory=dict)
    # Fields that are estimates rather than reported figures.
    estimated: set[str] = field(default_factory=set)


def quarter_end(ending_month: str) -> date:
    year, month = 2000 + int(ending_month[:2]), int(ending_month[3:])
    return date(year, month, calendar.monthrange(year, month)[1])


def has_quarterly_filings(facts: dict, *, since: date) -> bool:
    for concepts in facts.get("facts", {}).get("us-gaap", {}).values():
        for items in concepts.get("units", {}).values():
            if any(
                item.get("form") == "10-Q"
                and item.get("end", "") >= since.isoformat()
                for item in items
            ):
                return True
    return False


def extract_quarters(
    facts: dict,
    ending_months: dict[str, str],
) -> dict[str, QuarterValues]:
    # Standalone values for the quarters (id -> ending month) as reported
    # in SEC filings.
    us_gaap = facts.get("facts", {}).get("us-gaap", {})
    results = {
        quarter_id: QuarterValues() for quarter_id in ending_months
    }

    for field_name, (concepts, unit, scale) in FLOW_FIELDS.items():
        candidates = [
            _periods(us_gaap, concept, unit, scale) for concept in concepts
        ]
        for quarter_id, ending_month in ending_months.items():
            value = _first_standalone(candidates, quarter_end(ending_month))
            if value is None:
                continue
            if field_name in NON_NEGATIVE_FIELDS and value < 0:
                continue
            results[quarter_id].values[field_name] = value

    operating_cash_flow = [
        _periods(us_gaap, concept, "USD", MILLION)
        for concept in OPERATING_CASH_FLOW
    ]
    for quarter_id, ending_month in ending_months.items():
        values = results[quarter_id].values
        cash_flow = _first_standalone(
            operating_cash_flow,
            quarter_end(ending_month),
        )
        capex = values.get("reported_capex")
        if cash_flow is not None and capex is not None:
            values[FREE_CASH_FLOW_FIELD] = cash_flow - capex

    eps = [_periods(us_gaap, concept, "USD/shares", Decimal(1)) for concept in DILUTED_EPS]
    shares = [_periods(us_gaap, concept, "shares", MILLION) for concept in DILUTED_SHARES]
    for quarter_id, ending_month in ending_months.items():
        end = quarter_end(ending_month)
        quarter = results[quarter_id]
        reported_eps = _first_quarter(eps, end)
        reported_shares = _first_quarter(shares, end)
        if reported_eps is not None:
            quarter.values[EPS_FIELD] = reported_eps
        if reported_shares is not None:
            quarter.values[SHARES_FIELD] = reported_shares
        if reported_eps is None and reported_shares is None:
            _estimate_fourth_quarter(quarter, shares, end)
    return results


def _periods(
    us_gaap: dict,
    concept: str,
    unit: str,
    scale: Decimal,
) -> list[Period]:
    # One value per period, the one filed first: what the company reported
    # at the time rather than a later restatement.
    node = us_gaap.get(concept)
    if not node:
        return []
    first_filed: dict[tuple[str, str], dict] = {}
    for item in node.get("units", {}).get(unit, []):
        if item.get("form") not in FILINGS or "start" not in item:
            continue
        key = (item["start"], item["end"])
        known = first_filed.get(key)
        if known is None or item["filed"] < known["filed"]:
            first_filed[key] = item
    return [
        Period(
            start=date.fromisoformat(start),
            end=date.fromisoformat(end),
            value=Decimal(str(item["val"])) / scale,
        )
        for (start, end), item in first_filed.items()
    ]


def _first_standalone(
    candidates: Sequence[list[Period]],
    end: date,
) -> Decimal | None:
    for periods in candidates:
        value = _standalone(periods, end)
        if value is not None:
            return value
    return None


def _first_quarter(
    candidates: Sequence[list[Period]],
    end: date,
) -> Decimal | None:
    for periods in candidates:
        match = _quarter_fact(periods, end)
        if match is not None:
            return match.value
    return None


def _quarter_fact(periods: list[Period], end: date) -> Period | None:
    matches = [
        period
        for period in periods
        if QUARTER_DAYS[0] <= period.days <= QUARTER_DAYS[1]
        and abs((period.end - end).days) <= END_TOLERANCE_DAYS
    ]
    return min(matches, key=lambda p: abs((p.end - end).days), default=None)


def _standalone(periods: list[Period], end: date) -> Decimal | None:
    quarter = _quarter_fact(periods, end)
    if quarter is not None:
        return quarter.value
    # No three-month figure (cash flow statements, and every fourth
    # quarter): the cumulative figure less the one a quarter earlier, both
    # counted from the start of the same fiscal year.
    cumulative = [
        period
        for period in periods
        if QUARTER_DAYS[1] < period.days <= YEAR_DAYS[1]
        and abs((period.end - end).days) <= END_TOLERANCE_DAYS
    ]
    for current in sorted(cumulative, key=lambda p: abs((p.end - end).days)):
        for previous in periods:
            if (
                previous.start == current.start
                and QUARTER_DAYS[0]
                <= (current.end - previous.end).days
                <= QUARTER_DAYS[1]
            ):
                return current.value - previous.value
    return None


def _estimate_fourth_quarter(
    quarter: QuarterValues,
    shares: Sequence[list[Period]],
    end: date,
) -> None:
    # Neither a quarter's EPS nor its average share count is reported for
    # a fourth quarter, and neither can be recovered by subtraction. Both
    # are estimated from the year and marked as such.
    net_income = quarter.values.get("reported_net_income")
    if net_income is None:
        return
    for periods in shares:
        year = [
            period
            for period in periods
            if YEAR_DAYS[0] <= period.days <= YEAR_DAYS[1]
            and abs((period.end - end).days) <= END_TOLERANCE_DAYS
        ]
        if not year:
            continue
        fiscal_year = min(year, key=lambda p: abs((p.end - end).days))
        earlier = {
            period.end: period.value
            for period in periods
            if QUARTER_DAYS[0] <= period.days <= QUARTER_DAYS[1]
            and fiscal_year.start <= period.start
            and period.end < fiscal_year.end - timedelta(days=60)
        }
        if len(earlier) != 3:
            continue
        estimated_shares = 4 * fiscal_year.value - sum(earlier.values())
        average = sum(earlier.values()) / 3
        if (
            estimated_shares <= 0
            or abs(estimated_shares - average) / average
            > MAX_SHARE_ESTIMATE_DEVIATION
        ):
            continue
        quarter.values[SHARES_FIELD] = estimated_shares
        quarter.values[EPS_FIELD] = net_income / estimated_shares
        quarter.estimated.update(ESTIMATED_FIELDS)
        return
