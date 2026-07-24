import hashlib
import logging
from datetime import datetime
from urllib.parse import urlsplit

from firebase_admin import db

from error_reporting import ErrorReporter
from firebase_repository import parse_company_snapshot
from gemini.models import (
    Company,
    CompanyTarget,
    InstitutionRecord,
    Quarter,
    ReportDate,
)
from gemini.strings import LogMsg

companies_path = "company"
institutions_path = "institution"
data_root = "gemini"
logger = logging.getLogger(__name__)

def company_path(company_id: str) -> str:
    return companies_path + "/" + company_id + "/" + data_root


def create_target_id(company_id: str, target: CompanyTarget) -> str:
    source = target.source.casefold().rstrip("/")
    parsed_source = urlsplit(
        source if "://" in source else f"//{source}"
    )
    if parsed_source.hostname:
        source = (
            parsed_source.hostname.casefold()
            + parsed_source.path.rstrip("/")
        )
    institution = " ".join(target.institution.casefold().split())
    identity = f"{company_id}|{institution}|{source}"
    suffix = hashlib.sha256(identity.encode()).hexdigest()[:6]
    return f"{target.date.isoformat()}-{suffix}"


class FirebaseService:
    log = logger

    def __init__(
        self,
        error_reporter: ErrorReporter | None = None,
    ) -> None:
        self.errors = error_reporter

    def get_companies(self) -> dict[str, Company | None]:
        return parse_company_snapshot(
            db.reference(companies_path).get(),
            data_root=data_root,
            model=Company,
            logger=self.log,
            error_reporter=self.errors,
        )

    def get_institutions(self) -> dict[str, InstitutionRecord]:
        snapshot = db.reference(institutions_path).get()
        if snapshot is None:
            return {}
        if not isinstance(snapshot, dict):
            raise TypeError("Firebase /institution snapshot must be a mapping")
        return {
            institution_id: InstitutionRecord.model_validate(data)
            for institution_id, data in snapshot.items()
        }

    def create_institutions(
        self,
        institutions: dict[str, InstitutionRecord],
    ) -> None:
        if not institutions:
            return
        db.reference(institutions_path).update({
            institution_id: institution.model_dump(mode="json")
            for institution_id, institution in institutions.items()
        })
        self.log.info("Created %d institutions", len(institutions))

    def init_company(self, id: str, data: Company) -> None:
        db.reference(company_path(id)).set(data.model_dump(mode="json"))
        self.log.info(LogMsg.COMPANY_INIT.format(company_id=id, quarter_id=data.info.current_quarter_id, n_quarters=str(len(data.quarters))))

    def update_report_date(self, new_report_date: ReportDate) -> None:
        if new_report_date.report_date is None:
            raise ValueError("Cannot persist an unavailable report date")
        report_date_path = (
            "quarters/"
            + new_report_date.quarter
            + "/report_date_this_quarter"
        )
        company_reference = db.reference(company_path(new_report_date.ticker))
        previous_date = company_reference.child(report_date_path).get()
        company_reference.update({
            report_date_path: new_report_date.report_date.isoformat(),
            "info/last_update": datetime.now().strftime("%Y-%m-%d"),
        })
        self.log.info(LogMsg.REPORT_DATE_UPDATED.format(previous_date=previous_date, new_date=new_report_date.report_date, company_id=new_report_date.ticker, quarter_id=new_report_date.quarter))

    def report_quarter(self, company_id: str, quarter_data: Quarter) -> None:
        db.reference(company_path(company_id)).update({
            "quarters/" + quarter_data.id: quarter_data.model_dump(mode="json"),
            "info/last_update": datetime.now().strftime("%Y-%m-%d"),
        })
        self.log.info(LogMsg.QUARTER_REPORTED.format(company_id=company_id, quarter_id=quarter_data.id, date=quarter_data.report_date_this_quarter))

    def create_quarter(self, company_id, new_quarter_data: Quarter) -> None:
        db.reference(company_path(company_id)).update({
            "quarters/" + new_quarter_data.id: new_quarter_data.model_dump(mode="json"),
            "info/current_quarter_id": new_quarter_data.id,
            "info/last_update": datetime.now().strftime("%Y-%m-%d"),
        })
        self.log.info(LogMsg.QUARTER_CREATED.format(company_id=company_id, quarter_id=new_quarter_data.id))

    def upsert_target(
        self,
        company_id: str,
        target: CompanyTarget,
    ) -> str:
        target_id = create_target_id(company_id, target)
        db.reference(
            f"{company_path(company_id)}/targets/{target_id}"
        ).set(target.model_dump(mode="json"))
        self.log.info(
            LogMsg.TARGET_UPSERTED.format(
                company_id=company_id,
                target_id=target_id,
            )
        )
        return target_id
