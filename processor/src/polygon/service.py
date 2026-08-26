import hashlib
import json
import logging
from datetime import date

from firebase_admin import db

from error_reporting import ErrorReporter
from firebase_repository import (
    parse_company_snapshot,
    ticker_to_firebase_key,
)
from polygon.models import (
    CompanyNewsHistory,
    CompanySentimentAnalysis,
    NewsSentimentRecord,
)

companies_path = "company"
data_root = "pgn"
logger = logging.getLogger(__name__)


def company_path(company_id: str) -> str:
    company_key = ticker_to_firebase_key(company_id)
    return f"{companies_path}/{company_key}/{data_root}"


def create_sentiment_analysis_id(
    analysis: CompanySentimentAnalysis,
    analysis_date: date | None = None,
) -> str:
    record = NewsSentimentRecord.from_analysis(analysis)
    identity = json.dumps(
        record.model_dump(mode="json"),
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )
    suffix = hashlib.sha256(
        f"{analysis.ticker.casefold()}|{identity}".encode()
    ).hexdigest()[:6]
    created_on = analysis_date or date.today()
    return f"{created_on.isoformat()}-{suffix}"


class FirebaseService:
    log = logger

    def __init__(
        self,
        error_reporter: ErrorReporter | None = None,
    ) -> None:
        self.errors = error_reporter

    def get_companies(self) -> dict[str, CompanyNewsHistory | None]:
        return parse_company_snapshot(
            db.reference(companies_path).get(),
            data_root=data_root,
            model=CompanyNewsHistory,
            logger=self.log,
            error_reporter=self.errors,
        )

    def upsert_sentiment_analysis(
        self,
        analysis: CompanySentimentAnalysis,
    ) -> str:
        analysis_id = create_sentiment_analysis_id(analysis)
        record = NewsSentimentRecord.from_analysis(analysis)
        db.reference(
            f"{company_path(analysis.ticker)}/{analysis_id}"
        ).set(record.model_dump(mode="json"))
        self.log.info(
            "Upserted Polygon news sentiment analysis for %s as %s",
            analysis.ticker,
            analysis_id,
        )
        return analysis_id
