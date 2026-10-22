import logging
from datetime import date
from decimal import Decimal, InvalidOperation

from requests import Session
from requests.exceptions import RequestException

logger = logging.getLogger(__name__)


class FrankfurterClientError(RuntimeError):
    pass


class FrankfurterClient:
    # Free, key-less service publishing the ECB's daily reference rates;
    # the fallback behind Polygon for currency conversion.
    BASE_URL = "https://api.frankfurter.dev/v1"

    def __init__(
        self,
        session: Session | None = None,
        timeout: float = 15.0,
    ) -> None:
        self.session = session or Session()
        self.timeout = timeout

    def get_forex_daily_closes(
        self,
        base: str,
        quote: str,
        start: date,
        end: date,
    ) -> list[Decimal]:
        # Daily rates of the pair, oldest first: how many `quote` units
        # one `base` unit buys. ECB working days only.
        try:
            response = self.session.get(
                f"{self.BASE_URL}/{start.isoformat()}..{end.isoformat()}",
                params={"base": base, "symbols": quote},
                timeout=self.timeout,
            )
        except RequestException as exception:
            raise FrankfurterClientError(
                "Frankfurter request failed"
            ) from exception
        if response.status_code != 200:
            raise FrankfurterClientError(
                f"Frankfurter returned {response.status_code}: "
                f"{response.text}"
            )
        try:
            rates = response.json()["rates"]
            return [
                self._positive(day_rates[quote])
                for _day, day_rates in sorted(rates.items())
            ]
        except (ValueError, KeyError, TypeError, AttributeError,
                InvalidOperation) as exception:
            raise FrankfurterClientError(
                "Frankfurter returned an invalid response"
            ) from exception

    @staticmethod
    def _positive(value: object) -> Decimal:
        rate = Decimal(str(value))
        if not rate.is_finite() or rate <= 0:
            raise ValueError(f"invalid rate {value!r}")
        return rate
