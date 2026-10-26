import logging
import time
from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal

from requests import Session
from requests.exceptions import RequestException

from polygon.models import (
    AggregatesResponse,
    GroupedDailyResponse,
    NewsResponse,
    TickerOverview,
    TickerOverviewResponse,
)

logger = logging.getLogger(__name__)
RATE_LIMIT_MAX_RETRIES = 3
RATE_LIMIT_RETRY_DELAY_SECONDS = 30.0
# One grouped-daily response covers every US ticker (~12k bars, ~2 MB).
GROUPED_DAILY_TIMEOUT_SECONDS = 60.0
# A year or two of daily bars for one ticker.
AGGREGATES_TIMEOUT_SECONDS = 30.0


class PolygonClientError(RuntimeError):
    pass


@dataclass(frozen=True)
class DailyRange:
    day: date
    low: Decimal
    high: Decimal


class PolygonClient:
    BASE_URL = "https://api.polygon.io"
    NEWS_LIMIT = 1000

    def __init__(
        self,
        api_key: str,
        session: Session | None = None,
        timeout: float = 10.0,
        sleeper: Callable[[float], None] = time.sleep,
    ) -> None:
        self.session = session or Session()
        self.session.headers.update({
            "Accept": "application/json",
            "Authorization": f"Bearer {api_key}",
        })
        self.timeout = timeout
        self.sleeper = sleeper

    def get_latest_news(
        self,
        *,
        previous_days: int | None = 7,
        ticker: str | None = None,
        limit: int = NEWS_LIMIT,
    ) -> NewsResponse:
        if not 1 <= limit <= self.NEWS_LIMIT:
            raise ValueError(
                f"limit must be between 1 and {self.NEWS_LIMIT}"
            )
        parameters = {
            "sort": "published_utc",
            "order": "desc",
            "limit": limit,
        }
        if ticker is not None:
            parameters["ticker"] = ticker
        if previous_days is not None:
            if previous_days < 0:
                raise ValueError("previous_days must not be negative")
            today = date.today()
            start = today - timedelta(days=previous_days)
            end = today + timedelta(days=1)
            parameters.update({
                "published_utc.gte": f"{start.isoformat()}T00:00:00Z",
                "published_utc.lt": f"{end.isoformat()}T00:00:00Z",
            })

        return self._get_news(
            self.BASE_URL + "/v2/reference/news",
            parameters=parameters,
        )

    def get_grouped_daily(self, day: date) -> GroupedDailyResponse:
        # Daily bars for every US ticker in one call. A non-trading day
        # (weekend or holiday) comes back OK with no results.
        payload = self._get_json(
            f"{self.BASE_URL}/v2/aggs/grouped/locale/us/market/stocks/"
            f"{day.isoformat()}",
            parameters={"adjusted": "true"},
            timeout=GROUPED_DAILY_TIMEOUT_SECONDS,
        )
        try:
            return GroupedDailyResponse.model_validate(payload)
        except (TypeError, ValueError) as exception:
            raise PolygonClientError(
                "Polygon returned an invalid response"
            ) from exception

    def get_ticker_overview(self, ticker: str) -> TickerOverview:
        payload = self._get_json(
            f"{self.BASE_URL}/v3/reference/tickers/{ticker}",
        )
        try:
            return TickerOverviewResponse.model_validate(payload).results
        except (TypeError, ValueError) as exception:
            raise PolygonClientError(
                "Polygon returned an invalid response"
            ) from exception

    def get_daily_ranges(
        self,
        ticker: str,
        start: date,
        end: date,
    ) -> list[DailyRange]:
        # Split-adjusted daily low and high of every session between the
        # two dates (inclusive), oldest first.
        payload = self._get_json(
            f"{self.BASE_URL}/v2/aggs/ticker/{ticker}/range/1/day/"
            f"{start.isoformat()}/{end.isoformat()}",
            parameters={"adjusted": "true", "sort": "asc", "limit": 50000},
            timeout=AGGREGATES_TIMEOUT_SECONDS,
        )
        try:
            response = AggregatesResponse.model_validate(payload)
            return [
                DailyRange(
                    day=datetime.fromtimestamp(
                        bar.timestamp / 1000,
                        tz=timezone.utc,
                    ).date(),
                    low=bar.low,
                    high=bar.high,
                )
                for bar in response.results
            ]
        except (TypeError, ValueError) as exception:
            raise PolygonClientError(
                "Polygon returned an invalid response"
            ) from exception

    def get_forex_daily_closes(
        self,
        base: str,
        quote: str,
        start: date,
        end: date,
    ) -> list[Decimal]:
        # Daily closes of the pair, oldest first: how many `quote` units
        # one `base` unit buys (USD/KRW = 1500 means 1 USD = 1500 KRW).
        payload = self._get_json(
            f"{self.BASE_URL}/v2/aggs/ticker/C:{base}{quote}/range/1/day/"
            f"{start.isoformat()}/{end.isoformat()}",
            parameters={"adjusted": "true", "sort": "asc", "limit": 5000},
        )
        try:
            response = AggregatesResponse.model_validate(payload)
        except (TypeError, ValueError) as exception:
            raise PolygonClientError(
                "Polygon returned an invalid response"
            ) from exception
        return [bar.close for bar in response.results]

    def _get_news(
        self,
        url: str,
        *,
        parameters: dict[str, object] | None = None,
    ) -> NewsResponse:
        payload = self._get_json(url, parameters=parameters)
        try:
            return NewsResponse.model_validate(payload)
        except (TypeError, ValueError) as exception:
            raise PolygonClientError(
                "Polygon returned an invalid response"
            ) from exception

    def _get_json(
        self,
        url: str,
        *,
        parameters: dict[str, object] | None = None,
        timeout: float | None = None,
    ) -> object:
        request_arguments: dict[str, object] = {
            "timeout": self.timeout if timeout is None else timeout,
        }
        if parameters is not None:
            request_arguments["params"] = parameters

        retries = 0
        while True:
            try:
                response = self.session.get(
                    url,
                    **request_arguments,
                )
            except RequestException as exception:
                raise PolygonClientError(
                    "Polygon request failed"
                ) from exception

            if response.status_code != 429:
                break
            if retries >= RATE_LIMIT_MAX_RETRIES:
                logger.warning(
                    "Polygon is still rate limited after %d retries",
                    RATE_LIMIT_MAX_RETRIES,
                )
                break

            retries += 1
            logger.warning(
                "Polygon rate limited; retrying in %.0f seconds (%d/%d)",
                RATE_LIMIT_RETRY_DELAY_SECONDS,
                retries,
                RATE_LIMIT_MAX_RETRIES,
            )
            self.sleeper(RATE_LIMIT_RETRY_DELAY_SECONDS)

        if response.status_code != 200:
            raise PolygonClientError(
                f"Polygon returned {response.status_code}: {response.text}"
            )

        try:
            return response.json()
        except ValueError as exception:
            raise PolygonClientError(
                "Polygon returned an invalid response"
            ) from exception
