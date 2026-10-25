import logging
import time
from collections.abc import Callable
from datetime import date, timedelta

from requests import Session
from requests.exceptions import RequestException

from polygon.models import NewsResponse

logger = logging.getLogger(__name__)
RATE_LIMIT_MAX_RETRIES = 3
RATE_LIMIT_RETRY_DELAY_SECONDS = 30.0


class PolygonClientError(RuntimeError):
    pass


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

    def _get_news(
        self,
        url: str,
        *,
        parameters: dict[str, object] | None = None,
    ) -> NewsResponse:
        request_arguments: dict[str, object] = {
            "timeout": self.timeout,
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
            return NewsResponse.model_validate(response.json())
        except (TypeError, ValueError) as exception:
            raise PolygonClientError(
                "Polygon returned an invalid response"
            ) from exception
