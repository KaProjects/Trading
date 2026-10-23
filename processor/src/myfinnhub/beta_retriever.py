import logging
import time
from collections.abc import Callable, Mapping
from datetime import date

from error_reporting import ErrorReporter
from gemini.service import FirebaseService as GeminiFirebaseService
from indicators.models import BetaIndicator
from indicators.service import FirebaseService as IndicatorsFirebaseService
from myfinnhub.client import FinnhubClient

RUNNER_NAME = "FinnhubBeta"
# Finnhub's free plan allows 60 calls a minute.
REQUEST_SPACING_SECONDS = 1.1
logger = logging.getLogger(RUNNER_NAME)


class FinnhubBetaRetrieverRunner:
    log = logger
    name = RUNNER_NAME

    def __init__(
        self,
        finnhub_api_key: str | None = None,
        client: FinnhubClient | None = None,
        gemini_service: GeminiFirebaseService | None = None,
        indicators_service: IndicatorsFirebaseService | None = None,
        error_reporter: ErrorReporter | None = None,
        sleeper: Callable[[float], None] = time.sleep,
        clock: Callable[[], date] = date.today,
    ) -> None:
        self.errors = error_reporter or ErrorReporter(environment="local")
        if client is None:
            if finnhub_api_key is None:
                raise ValueError(
                    "finnhub_api_key is required without a client"
                )
            client = FinnhubClient(api_key=finnhub_api_key)

        self.client = client
        self.gemini_service = gemini_service or GeminiFirebaseService(
            error_reporter=self.errors,
        )
        self.indicators_service = (
            indicators_service
            or IndicatorsFirebaseService(error_reporter=self.errors)
        )
        self.sleeper = sleeper
        self.clock = clock

    def run(self) -> None:
        try:
            tickers = self._tracked_tickers()
            if not tickers:
                self.log.info("No companies to retrieve betas for")
                return
            self._retrieve_betas(tickers)
        except Exception as exception:
            self.report_error(exception, operation="run")

    def process_company(self, ticker: str) -> None:
        # Used when a company is onboarded; never raises, so it cannot
        # derail the remaining onboarding steps.
        try:
            self._retrieve_betas([ticker])
        except Exception as exception:
            self.report_error(
                exception,
                operation="process_company",
                context={"ticker": ticker},
            )

    def _tracked_tickers(self) -> list[str]:
        # Only fully initialized gemini companies, as for prices: a
        # placeholder must never get a child node written.
        companies = self.gemini_service.get_companies()
        return sorted(
            ticker
            for ticker, company in companies.items()
            if company is not None
        )

    def _retrieve_betas(self, tickers: list[str]) -> None:
        retrieved_on = self.clock()
        stored = 0
        unavailable: list[str] = []
        failed: dict[str, str] = {}
        for index, ticker in enumerate(tickers):
            if index:
                self.sleeper(REQUEST_SPACING_SECONDS)
            # Isolated per ticker: one failure costs only that ticker.
            try:
                value = self.client.get_beta(ticker)
                if value is None:
                    unavailable.append(ticker)
                    continue
                self.indicators_service.upsert_beta(
                    ticker,
                    BetaIndicator(value=value, retrieved_on=retrieved_on),
                )
            except Exception as exception:
                self.log.exception("Beta for %s failed", ticker)
                failed[ticker] = f"{type(exception).__name__}: {exception}"
                continue
            stored += 1

        if unavailable:
            self.errors.report_warning_message(
                "Finnhub has no beta for: " + ", ".join(unavailable),
                logger=self.log,
                source=self.name,
                operation="retrieve_betas",
                context={"unavailable_count": len(unavailable)},
            )
        if failed:
            # One message however many tickers failed: an outage would
            # otherwise post one error per company.
            self.errors.report_error_message(
                "Could not retrieve or store beta for: "
                + "; ".join(f"{ticker} ({reason})" for ticker, reason in failed.items()),
                logger=self.log,
                source=self.name,
                operation="retrieve_betas",
                context={
                    "failed_count": len(failed),
                    "retrieved_on": retrieved_on.isoformat(),
                },
            )
        self.log.info(
            "Stored %d beta(s); %d unavailable, %d failed",
            stored,
            len(unavailable),
            len(failed),
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
