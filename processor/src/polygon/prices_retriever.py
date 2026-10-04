import logging
from collections.abc import Callable, Mapping
from datetime import date, timedelta
from decimal import Decimal

from error_reporting import ErrorReporter
from gemini.service import FirebaseService as GeminiFirebaseService
from indicators.models import PriceIndicator
from indicators.service import FirebaseService as IndicatorsFirebaseService
from polygon.client import PolygonClient

RUNNER_NAME = "PolygonPrices"
# Weekends are skipped locally; this only bounds how far back market
# holidays (or a not-yet-published session) can push the lookup.
MAX_WEEKDAY_LOOKBACK = 5
logger = logging.getLogger(RUNNER_NAME)


class PolygonPricesRetrieverRunner:
    log = logger
    name = RUNNER_NAME

    def __init__(
        self,
        polygon_api_key: str | None = None,
        client: PolygonClient | None = None,
        gemini_service: GeminiFirebaseService | None = None,
        indicators_service: IndicatorsFirebaseService | None = None,
        error_reporter: ErrorReporter | None = None,
        clock: Callable[[], date] = date.today,
    ) -> None:
        self.errors = error_reporter or ErrorReporter(environment="local")
        if client is None:
            if polygon_api_key is None:
                raise ValueError(
                    "polygon_api_key is required without a client"
                )
            client = PolygonClient(api_key=polygon_api_key)

        self.client = client
        self.gemini_service = gemini_service or GeminiFirebaseService(
            error_reporter=self.errors,
        )
        self.indicators_service = (
            indicators_service
            or IndicatorsFirebaseService(error_reporter=self.errors)
        )
        self.clock = clock

    def run(self) -> None:
        try:
            tickers = self._tracked_tickers()
            if not tickers:
                self.log.info("No companies to retrieve prices for")
                return

            trading_day, closes = self._latest_closes()
            if trading_day is None:
                self.errors.report_error_message(
                    "Polygon returned no daily bars for any of the last "
                    f"{MAX_WEEKDAY_LOOKBACK} weekdays; no prices were stored.",
                    logger=self.log,
                    source=self.name,
                    operation="retrieve_prices",
                )
                return

            self._persist_prices(tickers, trading_day, closes)
        except Exception as exception:
            self.report_error(exception, operation="run")

    def _tracked_tickers(self) -> list[str]:
        # Only fully initialized gemini companies: a company still sitting
        # as a placeholder (onboarding pending or retrying) must never get
        # a child node written, or it stops being a placeholder and the
        # onboarding watcher would no longer retry it.
        companies = self.gemini_service.get_companies()
        return sorted(
            ticker
            for ticker, company in companies.items()
            if company is not None
        )

    def _latest_closes(self) -> tuple[date | None, dict[str, Decimal]]:
        first_day = day = self._previous_weekday(self.clock())
        for _ in range(MAX_WEEKDAY_LOOKBACK):
            response = self.client.get_grouped_daily(day)
            if response.results:
                if day != first_day:
                    # Indistinguishable from a not-yet-published session,
                    # and the stored retrieved_on would make an older close
                    # look current - so say so instead of staying silent.
                    self.errors.report_warning_message(
                        f"Polygon had no daily bars for "
                        f"{first_day.isoformat()} (market holiday, or the "
                        "session is not published yet); stored the "
                        f"{day.isoformat()} close instead.",
                        logger=self.log,
                        source=self.name,
                        operation="retrieve_prices",
                        context={
                            "requested_date": first_day.isoformat(),
                            "used_date": day.isoformat(),
                        },
                    )
                return day, {bar.ticker: bar.close for bar in response.results}
            self.log.info(
                "No Polygon bars for %s (market holiday?); trying the "
                "previous weekday",
                day.isoformat(),
            )
            day = self._previous_weekday(day)
        return None, {}

    @staticmethod
    def _previous_weekday(day: date) -> date:
        day -= timedelta(days=1)
        while day.weekday() >= 5:
            day -= timedelta(days=1)
        return day

    def _persist_prices(
        self,
        tickers: list[str],
        trading_day: date,
        closes: Mapping[str, Decimal],
    ) -> None:
        # The stored retrieved_on is the day of this retrieval (not the
        # session the close belongs to), so a later consumer can tell whether
        # today's retrieval ran. The close itself is always the latest one
        # available, which on weekends and holidays is the last session.
        retrieved_on = self.clock()
        stored = 0
        missing = []
        for ticker in tickers:
            close = closes.get(ticker)
            if close is None:
                missing.append(ticker)
                continue
            try:
                self.indicators_service.upsert_price(
                    ticker,
                    PriceIndicator(value=close, retrieved_on=retrieved_on),
                )
            except Exception as exception:
                self.report_error(
                    exception,
                    operation="persist_price",
                    context={
                        "ticker": ticker,
                        "retrieved_on": retrieved_on.isoformat(),
                    },
                )
                continue
            stored += 1

        if missing:
            self.errors.report_warning_message(
                f"No Polygon price for {trading_day.isoformat()} was found "
                "for: " + ", ".join(missing),
                logger=self.log,
                source=self.name,
                operation="retrieve_prices",
                context={
                    "date": trading_day.isoformat(),
                    "missing_count": len(missing),
                },
            )
        self.log.info(
            "Stored %d price(s) for %s; %d not found",
            stored,
            trading_day.isoformat(),
            len(missing),
        )

    def report_error(
        self,
        exception: BaseException,
        *,
        operation: str,
        context: Mapping[str, object] | None = None,
    ) -> str:
        return self.errors.report(
            exception,
            logger=self.log,
            source=self.name,
            operation=operation,
            context=context,
        )
