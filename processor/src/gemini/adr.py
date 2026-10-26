import logging
from dataclasses import dataclass
from decimal import Decimal

from gemini.models import Quarter
from myfinnhub.client import FinnhubClient
from polygon.client import PolygonClient

logger = logging.getLogger(__name__)
ADR_TYPE = "ADRC"
MILLION = Decimal(1_000_000)
# ADS per ordinary share, as the depositaries actually issue them.
KNOWN_RATIOS = tuple(
    Decimal(ratio)
    for ratio in (
        "0.1", "0.2", "0.25", "0.5", "1", "2", "3", "4", "5", "8", "10",
        "15", "20", "25", "30", "40", "50", "100", "200", "500", "1000",
    )
)
# The two share counts come from different providers on different days.
MAX_RATIO_DEVIATION = Decimal("0.05")


@dataclass(frozen=True)
class AdsRatio:
    ads_per_share: Decimal
    ads_shares: Decimal
    ordinary_shares: Decimal


def snap_ratio(ads_shares: Decimal, ordinary_shares: Decimal) -> Decimal | None:
    # The nearest ratio depositaries use, if the counts are close to one.
    if ads_shares <= 0 or ordinary_shares <= 0:
        return None
    measured = ads_shares / ordinary_shares
    nearest = min(KNOWN_RATIOS, key=lambda ratio: abs(ratio - measured) / ratio)
    if abs(nearest - measured) / nearest > MAX_RATIO_DEVIATION:
        return None
    return nearest


class AdsRatioDetector:
    log = logger

    def __init__(self, polygon: PolygonClient, finnhub: FinnhubClient) -> None:
        self.polygon = polygon
        self.finnhub = finnhub

    def detect(self, ticker: str) -> AdsRatio | None:
        # Polygon counts an ADR's shares in ADSs, while Finnhub's company
        # profile counts the ordinary shares: their quotient is the ratio.
        # None for a stock that is not an ADR, or when the counts do not
        # settle on a ratio depositaries use. A ratio of 1 means nothing
        # needs scaling and is not reported either.
        overview = self.polygon.get_ticker_overview(ticker)
        if overview.type != ADR_TYPE or overview.weighted_shares_outstanding is None:
            return None
        ordinary_millions = self.finnhub.get_shares_outstanding(ticker)
        if ordinary_millions is None:
            return None
        ordinary = ordinary_millions * MILLION
        ratio = snap_ratio(overview.weighted_shares_outstanding, ordinary)
        if ratio is None or ratio == 1:
            return None
        return AdsRatio(
            ads_per_share=ratio,
            ads_shares=overview.weighted_shares_outstanding,
            ordinary_shares=ordinary,
        )


def scale_to_ads(quarter: Quarter, ads_per_share: Decimal | None) -> Quarter:
    # Per-share amounts fall and the share count rises by the ratio.
    if ads_per_share is None or ads_per_share == 1:
        return quarter
    updates = {}
    if quarter.reported_eps is not None:
        updates["reported_eps"] = quarter.reported_eps / ads_per_share
    if quarter.reported_shares is not None:
        updates["reported_shares"] = quarter.reported_shares * ads_per_share
    return quarter.model_copy(update=updates)
