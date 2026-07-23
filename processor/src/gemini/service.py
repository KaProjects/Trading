import logging
from datetime import datetime

from firebase_admin import db

from firebase_repository import parse_company_snapshot
from gemini.models import Company, ReportDate, Quarter
from gemini.strings import LogMsg

companies_path = "company"
data_root = "gemini"
logger = logging.getLogger(__name__)

def company_path(company_id: str) -> str:
    return companies_path + "/" + company_id + "/" + data_root

class FirebaseService:
    log = logger

    def get_companies(self) -> dict[str, Company | None]:
        return parse_company_snapshot(
            db.reference(companies_path).get(),
            data_root=data_root,
            model=Company,
            logger=self.log,
        )

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
