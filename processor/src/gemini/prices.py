import logging
from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from polygon.client import PolygonClient

logger = logging.getLogger(__name__)
# Quarters whose prices come from Polygon are those traded in USD.
POLYGON_PRICE_CURRENCY = "$"


@dataclass(frozen=True)
class PriceRange:
    low: Decimal
    high: Decimal


def trading_currency(info) -> str:
    # What the stock is quoted in: the override when the financials are
    # converted into the currency it trades in, else the reporting one.
    return info.currency_override or info.currency


def prices_come_from_polygon(info) -> bool:
    return trading_currency(info) == POLYGON_PRICE_CURRENCY


class QuarterPriceRanges:
    log = logger

    def __init__(self, client: PolygonClient) -> None:
        self.client = client

    def ranges(
        self,
        ticker: str,
        windows: dict[str, tuple[date, date]],
    ) -> dict[str, PriceRange | None]:
        # The lowest low and highest high of the sessions strictly between
        # each window's two report dates (the report days themselves are
        # left out). One request covers every window; a window without a
        # single session - before the stock was listed - maps to None.
        if not windows:
            return {}
        start = min(first for first, _last in windows.values())
        end = max(last for _first, last in windows.values())
        bars = self.client.get_daily_ranges(ticker, start, end)

        ranges: dict[str, PriceRange | None] = {}
        for quarter_id, (first, last) in windows.items():
            inside = [bar for bar in bars if first < bar.day < last]
            ranges[quarter_id] = (
                PriceRange(
                    low=min(bar.low for bar in inside),
                    high=max(bar.high for bar in inside),
                )
                if inside
                else None
            )
        return ranges
