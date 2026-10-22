import calendar
import logging
from typing import Protocol
from datetime import date, timedelta
from decimal import Decimal

from gemini.models import FxRate, Quarter

logger = logging.getLogger(__name__)
# ~63 weekdays in a quarter; far fewer bars means the feed has a gap and
# the average would not represent the period.
MIN_RATE_OBSERVATIONS = 40
RATE_PLACES = Decimal("0.000001")
AMOUNT_PLACES = Decimal("0.0001")
MONTHS_PER_QUARTER = 3
# How far back to look for the latest daily close (weekends, holidays).
LATEST_RATE_LOOKBACK_DAYS = 10
PRIMARY_SOURCE = "polygon"
FALLBACK_SOURCE = "frankfurter"
# Symbols a company's `currency` may carry, mapped to ISO codes. "$" is
# USD by convention here; a 3-letter code is taken as is.
CURRENCY_CODES = {
    "$": "USD",
    "€": "EUR",
    "£": "GBP",
    "₩": "KRW",
}
# Monetary totals and per-share amounts; shares (a count) and the stock
# prices (already in the trading currency) are never converted.
CONVERTED_FIELDS = (
    "reported_eps",
    "reported_revenues",
    "reported_gross_profit",
    "reported_operating_income",
    "reported_net_income",
    "reported_capex",
    "reported_free_cash_flow",
    "reported_div",
)


class CurrencyConversionError(RuntimeError):
    pass


class RateSource(Protocol):
    def get_forex_daily_closes(
        self,
        base: str,
        quote: str,
        start: date,
        end: date,
    ) -> list[Decimal]: ...


def currency_code(symbol: str) -> str:
    code = CURRENCY_CODES.get(symbol)
    if code is not None:
        return code
    if len(symbol) == 3 and symbol.isalpha():
        return symbol.upper()
    raise CurrencyConversionError(
        f"Unknown currency symbol {symbol!r}; add it to CURRENCY_CODES"
    )


def quarter_period(quarter: Quarter) -> tuple[date, date]:
    return _period_ending(
        2000 + int(quarter.ending_month[:2]),
        int(quarter.ending_month[3:]),
    )


def calendar_quarter_period(quarter_id: str) -> tuple[date, date]:
    # For a quarter known only by its YYQX id, assume a calendar fiscal
    # year.
    return _period_ending(2000 + int(quarter_id[:2]), int(quarter_id[3]) * 3)


def _period_ending(year: int, month: int) -> tuple[date, date]:
    last_day = calendar.monthrange(year, month)[1]
    first_month = month - (MONTHS_PER_QUARTER - 1)
    first_year = year
    if first_month < 1:
        first_month += 12
        first_year -= 1
    return date(first_year, first_month, 1), date(year, month, last_day)


class QuarterCurrencyConverter:
    log = logger

    def __init__(
        self,
        client: RateSource,
        fallback: RateSource | None = None,
    ) -> None:
        # Sources are tried in order; a quarter's rate always comes from a
        # single one of them and is recorded together with its name.
        self.sources: list[tuple[str, RateSource]] = [(PRIMARY_SOURCE, client)]
        if fallback is not None:
            self.sources.append((FALLBACK_SOURCE, fallback))

    def convert(
        self,
        quarter: Quarter,
        *,
        from_currency: str,
        to_currency: str,
    ) -> tuple[Quarter, FxRate]:
        # Returns the quarter in `to_currency` and the rate used: units of
        # `from_currency` per one unit of `to_currency`, averaged over the
        # quarter (the usual rate for income-statement and cash-flow
        # figures).
        fx_rate = self.average_rate(
            quarter,
            from_currency=from_currency,
            to_currency=to_currency,
        )
        converted = self.convert_amounts(
            {field: getattr(quarter, field) for field in CONVERTED_FIELDS},
            fx_rate.rate,
        )
        self.log.info(
            "Converted %s from %s to %s at %s (%s)",
            quarter.id,
            from_currency,
            to_currency,
            fx_rate.rate,
            fx_rate.source,
        )
        return quarter.model_copy(update=converted), fx_rate

    def average_rate(
        self,
        quarter: Quarter,
        *,
        from_currency: str,
        to_currency: str,
    ) -> FxRate:
        start, end = quarter_period(quarter)
        if end >= date.today():
            raise CurrencyConversionError(
                f"Quarter {quarter.id} has not ended yet ({end})"
            )
        return self.average_rate_for_period(
            start,
            end,
            from_currency=from_currency,
            to_currency=to_currency,
        )

    def rate_for_period(
        self,
        start: date,
        end: date,
        *,
        from_currency: str,
        to_currency: str,
    ) -> FxRate:
        # The average over a period that has ended, the latest close for
        # one still running or yet to come (an estimate has no final rate).
        if end < date.today():
            return self.average_rate_for_period(
                start,
                end,
                from_currency=from_currency,
                to_currency=to_currency,
            )
        return self.latest_rate(
            from_currency=from_currency,
            to_currency=to_currency,
        )

    def average_rate_for_period(
        self,
        start: date,
        end: date,
        *,
        from_currency: str,
        to_currency: str,
    ) -> FxRate:
        source, closes = self._closes(
            from_currency,
            to_currency,
            start,
            end,
            minimum=MIN_RATE_OBSERVATIONS,
        )
        return FxRate(
            rate=(sum(closes) / len(closes)).quantize(RATE_PLACES),
            source=source,
        )

    def latest_rate(
        self,
        *,
        from_currency: str,
        to_currency: str,
    ) -> FxRate:
        today = date.today()
        source, closes = self._closes(
            from_currency,
            to_currency,
            today - timedelta(days=LATEST_RATE_LOOKBACK_DAYS),
            today,
            minimum=1,
        )
        return FxRate(rate=closes[-1].quantize(RATE_PLACES), source=source)

    def convert_amounts(
        self,
        amounts: dict[str, Decimal | None],
        rate: Decimal,
    ) -> dict[str, Decimal | None]:
        return {
            name: None if value is None else (value / rate).quantize(AMOUNT_PLACES)
            for name, value in amounts.items()
        }

    def _closes(
        self,
        from_currency: str,
        to_currency: str,
        start: date,
        end: date,
        *,
        minimum: int,
    ) -> tuple[str, list[Decimal]]:
        pair = (currency_code(to_currency), currency_code(from_currency))
        failures = []
        for name, source in self.sources:
            try:
                closes = source.get_forex_daily_closes(*pair, start, end)
            except Exception as exception:
                failures.append(f"{name}: {exception}")
                self.log.warning(
                    "%s could not provide %s/%s rates: %s",
                    name,
                    pair[0],
                    pair[1],
                    exception,
                )
                continue
            if len(closes) >= minimum:
                return name, closes
            failures.append(f"{name}: only {len(closes)} daily rates")
            self.log.warning(
                "%s has only %d daily %s/%s rates between %s and %s; "
                "at least %d are needed",
                name,
                len(closes),
                pair[0],
                pair[1],
                start,
                end,
                minimum,
            )
        raise CurrencyConversionError(
            f"No usable {pair[0]}/{pair[1]} rates between {start} and "
            f"{end} ({'; '.join(failures)})"
        )
