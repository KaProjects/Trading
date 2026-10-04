import logging
from collections.abc import Iterable

from firebase_admin import db
from pydantic import ValidationError

from error_reporting import ErrorReporter
from firebase_repository import ticker_to_firebase_key
from indicators.models import PriceIndicator

companies_path = "company"
data_root = "indicators"
PRICE_KEY = "price"
logger = logging.getLogger(__name__)


def company_path(company_id: str) -> str:
    company_key = ticker_to_firebase_key(company_id)
    return f"{companies_path}/{company_key}/{data_root}"


def price_path(company_id: str) -> str:
    return f"{company_path(company_id)}/{PRICE_KEY}"


class FirebaseService:
    log = logger

    def __init__(
        self,
        error_reporter: ErrorReporter | None = None,
    ) -> None:
        self.errors = error_reporter

    def upsert_price(
        self,
        company_id: str,
        price: PriceIndicator,
    ) -> None:
        # update() merges into the price object, so attributes another job
        # may add to it later survive a price refresh. Unlike the
        # gemini/finnhub/polygon services this deliberately does not call
        # mark_company_enabled: it only enriches companies that already
        # exist and must never become another way to create one.
        db.reference(price_path(company_id)).update(
            price.model_dump(mode="json")
        )
        self.log.info(
            "Upserted price for %s (%s as of %s)",
            company_id,
            price.value,
            price.retrieved_on.isoformat(),
        )

    def get_prices(
        self,
        company_ids: Iterable[str],
    ) -> dict[str, PriceIndicator]:
        # Per-company reads: callers only need the handful of tickers that
        # have something to post, not a download of the whole company tree.
        prices: dict[str, PriceIndicator] = {}
        for company_id in company_ids:
            # Isolated per company: one unreadable or malformed price must
            # not cost every other ticker its price line.
            try:
                data = db.reference(price_path(company_id)).get()
                if data is None:
                    continue
                prices[company_id] = PriceIndicator.model_validate(data)
            except Exception as exception:
                self._report_price_failure(company_id, exception)
        return prices

    def _report_price_failure(
        self,
        company_id: str,
        exception: Exception,
    ) -> None:
        operation = (
            "parse_price"
            if isinstance(exception, ValidationError)
            else "read_price"
        )
        if self.errors is None:
            self.log.error(
                "Ignoring unusable price for company %s (%s): %s",
                company_id,
                operation,
                exception,
            )
            return
        self.errors.report(
            exception,
            logger=self.log,
            source="IndicatorsService",
            operation=operation,
            context={"company_id": company_id},
        )
