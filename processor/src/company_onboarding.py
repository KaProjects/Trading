import logging

from firebase_admin import db

from discord.client import DiscordClient
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
        discord: DiscordClient,
        error_reporter: ErrorReporter,
    ) -> None:
        self.gemini = gemini
        self.finnhub = finnhub
        self.polygon = polygon
        self.discord = discord
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
            # Initial connection, or a reconnect resync: a placeholder
            # still sitting here (never attempted, or a previous attempt
            # that failed without ever writing real data) is retried, same
            # as if it had just been added. There is no separate "already
            # failed" record - the node staying a placeholder *is* that
            # record, and it is naturally cleared by deleting the node.
            if isinstance(event.data, dict):
                for company_key, company_data in event.data.items():
                    self._handle_company_change(company_key, company_data)
            return

        company_key = segments[0]

        if len(segments) == 1 and event.data is None:
            # Removed: a later re-add is a genuinely new addition again,
            # which is how a failed onboarding attempt gets retried without
            # waiting for a restart.
            self._known_company_keys.discard(company_key)
            return

        if len(segments) != 1:
            # Nested write under an already-existing company; not relevant
            # to onboarding.
            self._known_company_keys.add(company_key)
            return

        self._handle_company_change(company_key, event.data)

    def _handle_company_change(
        self,
        company_key: str,
        company_data: object,
    ) -> None:
        if company_key in self._known_company_keys:
            return
        self._known_company_keys.add(company_key)

        if isinstance(company_data, dict):
            # Already has real data from a previous successful onboarding.
            return

        self._onboard(ticker_from_firebase_key(company_key))

    def _onboard(self, ticker: str) -> None:
        self.log.info("Onboarding new company %s", ticker)
        company = self.gemini.onboard_company(ticker)
        if company is None:
            return
        self.finnhub.process_company(ticker, None)
        self.polygon.process_company(ticker)

        self.log.info("Onboarding completed for %s", ticker)
        self.discord.post_eventlog({
            "content": f"✅ Onboarding completed for {ticker}",
        })
