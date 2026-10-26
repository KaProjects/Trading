import logging
from collections.abc import Callable, Mapping
from datetime import date

from error_reporting import ErrorReporter
from gemini.adr import AdsRatioDetector
from gemini.service import FirebaseService as GeminiFirebaseService

RUNNER_NAME = "AdsRatio"
logger = logging.getLogger(RUNNER_NAME)


class AdsRatioMonitorRunner:
    log = logger
    name = RUNNER_NAME

    def __init__(
        self,
        detector: AdsRatioDetector,
        gemini_service: GeminiFirebaseService | None = None,
        error_reporter: ErrorReporter | None = None,
        clock: Callable[[], date] = date.today,
    ) -> None:
        self.errors = error_reporter or ErrorReporter(environment="local")
        self.detector = detector
        self.gemini_service = gemini_service or GeminiFirebaseService(
            error_reporter=self.errors,
        )
        self.clock = clock

    def run(self) -> None:
        # Re-derives the ratio of every company that has one and only
        # reports a difference: a changed ratio rescales every new EPS and
        # share figure, so it is for a person to confirm, not to apply.
        try:
            companies = self.gemini_service.get_companies()
            problems: list[str] = []
            for ticker, company in sorted(companies.items()):
                if company is None or company.info.ads_per_share is None:
                    continue
                stored = company.info.ads_per_share
                try:
                    detected = self.detector.detect(ticker)
                except Exception as exception:
                    problems.append(f"{ticker}: check failed ({exception})")
                    continue
                if detected is None:
                    problems.append(
                        f"{ticker}: the stored ratio {stored:f} could not "
                        "be confirmed"
                    )
                elif detected.ads_per_share != stored:
                    problems.append(
                        f"{ticker}: the market data now implies "
                        f"{detected.ads_per_share:f} ADSs per share, not the "
                        f"stored {stored:f}"
                    )
                else:
                    self._record_check(ticker, problems)
            if problems:
                self.errors.report_warning_message(
                    "ADS ratio check:\n" + "\n".join(problems),
                    logger=self.log,
                    source=self.name,
                    operation="check_ads_ratios",
                    context={"problem_count": len(problems)},
                )
        except Exception as exception:
            self.report_error(exception, operation="run")

    def _record_check(self, ticker: str, problems: list[str]) -> None:
        try:
            self.gemini_service.update_ads_check(ticker, self.clock())
        except Exception as exception:
            problems.append(f"{ticker}: recording the check failed ({exception})")

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
