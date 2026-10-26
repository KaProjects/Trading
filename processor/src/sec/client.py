import logging
import time
from collections.abc import Callable

from requests import Session
from requests.exceptions import RequestException

logger = logging.getLogger(__name__)
# SEC allows 10 requests a second per IP and blocks for a while beyond it;
# stay well inside.
MIN_REQUEST_INTERVAL_SECONDS = 0.2
RETRY_DELAY_SECONDS = 10.0
MAX_RETRIES = 3
RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 504}


class SecClientError(RuntimeError):
    pass


class SecClient:
    TICKERS_URL = "https://www.sec.gov/files/company_tickers.json"
    FACTS_URL = "https://data.sec.gov/api/xbrl/companyfacts/CIK{cik}.json"

    def __init__(
        self,
        contact_email: str,
        session: Session | None = None,
        timeout: float = 60.0,
        sleeper: Callable[[float], None] = time.sleep,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self.session = session or Session()
        # SEC rejects requests that do not identify who is asking.
        self.session.headers.update({
            "User-Agent": f"TradingProcessor {contact_email}",
            "Accept-Encoding": "gzip, deflate",
        })
        self.timeout = timeout
        self.sleeper = sleeper
        self.clock = clock
        self._last_request_at: float | None = None
        self._tickers: dict | None = None

    def get_cik(self, ticker: str) -> str | None:
        # SEC lists share classes with a dash (BRK-B), we with a dot.
        if self._tickers is None:
            # About 800 KB, and it changes rarely: fetched once per client.
            self._tickers = self._get_json(self.TICKERS_URL)
        payload = self._tickers
        wanted = {ticker.upper(), ticker.upper().replace(".", "-")}
        try:
            for entry in payload.values():
                if entry["ticker"].upper() in wanted:
                    return str(entry["cik_str"]).zfill(10)
        except (AttributeError, KeyError, TypeError) as exception:
            raise SecClientError(
                "SEC returned an invalid ticker list"
            ) from exception
        return None

    def get_company_facts(self, cik: str) -> dict:
        payload = self._get_json(self.FACTS_URL.format(cik=cik.zfill(10)))
        if not isinstance(payload, dict) or not isinstance(
            payload.get("facts"),
            dict,
        ):
            raise SecClientError("SEC returned invalid company facts")
        return payload

    def _get_json(self, url: str) -> dict:
        retries = 0
        while True:
            self._pace()
            try:
                response = self.session.get(url, timeout=self.timeout)
            except RequestException as exception:
                raise SecClientError("SEC request failed") from exception
            if (
                response.status_code in RETRYABLE_STATUS_CODES
                and retries < MAX_RETRIES
            ):
                retries += 1
                logger.warning(
                    "SEC returned %d; retrying in %.0f seconds (%d/%d)",
                    response.status_code,
                    RETRY_DELAY_SECONDS,
                    retries,
                    MAX_RETRIES,
                )
                self.sleeper(RETRY_DELAY_SECONDS)
                continue
            break

        if response.status_code != 200:
            raise SecClientError(
                f"SEC returned {response.status_code}: {response.text[:200]}"
            )
        try:
            return response.json()
        except ValueError as exception:
            raise SecClientError("SEC returned an invalid response") from exception

    def _pace(self) -> None:
        now = self.clock()
        if self._last_request_at is not None:
            wait = MIN_REQUEST_INTERVAL_SECONDS - (now - self._last_request_at)
            if wait > 0:
                self.sleeper(wait)
                now = self.clock()
        self._last_request_at = now
