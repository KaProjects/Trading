import logging

from firebase_admin import db

from error_reporting import ErrorReporter
from firebase_repository import ticker_from_firebase_key
from gemini.retriever import StockDataRetrieverRunner
from myfinnhub.retriever import FinnhubEarningsRetrieverRunner
from polygon.retriever import PolygonNewsRetrieverRunner

RUNNER_NAME = "CompanyOnboarding"
COMPANIES_PATH = "company"
logger = logging.getLogger(RUNNER_NAME)


class CompanyOnboardingWatcher:
    log = logger
    name = RUNNER_NAME

    def __init__(
        self,
        *,
        gemini: StockDataRetrieverRunner,
        finnhub: FinnhubEarningsRetrieverRunner,
        polygon: PolygonNewsRetrieverRunner,
        error_reporter: ErrorReporter,
    ) -> None:
        self.gemini = gemini
        self.finnhub = finnhub
        self.polygon = polygon
        self.errors = error_reporter
        self._known_company_keys: set[str] = set()

    def start(self):
        return db.reference(COMPANIES_PATH).listen(self._on_event)

    def _on_event(self, event) -> None:
        try:
            self._handle_event(event)
        except Exception as exception:
            self.errors.report(
                exception,
                logger=self.log,
                source=self.name,
                operation="handle_company_event",
                context={
                    "path": event.path,
                    "event_type": event.event_type,
                },
            )

    def _handle_event(self, event) -> None:
        segments = [
            segment for segment in event.path.split("/") if segment
        ]

        if not segments:
            # Initial connection, or a reconnect resync: seeds the known
            # set from the full snapshot without onboarding anything that
            # already existed before this listener started watching.
            if isinstance(event.data, dict):
                self._known_company_keys.update(event.data.keys())
            return

        company_key = segments[0]

        if len(segments) == 1 and event.data is None:
            # Removed: a later re-add is a genuinely new addition again,
            # which is how a failed onboarding attempt gets retried.
            self._known_company_keys.discard(company_key)
            return

        if company_key in self._known_company_keys:
            return
        self._known_company_keys.add(company_key)

        if len(segments) != 1 or isinstance(event.data, dict):
            # Not a fresh placeholder node (e.g. a nested write for a
            # company whose top-level creation we never observed) - ignore.
            return

        self._onboard(ticker_from_firebase_key(company_key))

    def _onboard(self, ticker: str) -> None:
        self.log.info("Onboarding new company %s", ticker)
        company = self.gemini.onboard_company(ticker)
        if company is None:
            return
        self.finnhub.process_company(ticker, None)
        self.polygon.process_company(ticker)
