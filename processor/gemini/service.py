from datetime import datetime

from firebase_admin import db

from gemini.models import Company, ReportDate, Quarter
from gemini.strings import LogMsg
from utils import BaseClass

companies_path = "company"
data_root = "gemini"

def company_path(company_id: str) -> str:
    return companies_path + "/" + company_id + "/" + data_root

class FirebaseService(BaseClass):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)

    def get_companies(self):
        companies_data: dict = db.reference(companies_path).get()
        companies: dict[str, Company] = dict()
        for company_id in companies_data:
            if not isinstance(companies_data[company_id], dict) or companies_data[company_id].get(data_root) is None:
                companies[company_id] = None
            else:
                companies[company_id] = Company.model_validate(companies_data[company_id][data_root])
        return companies

    def init_company(self, id: str, data: Company) -> None:
        db.reference(company_path(id)).set(data.model_dump())
        self.log(LogMsg.COMPANY_INIT.format(company_id=id, quarter_id=data.info.current_quarter_id, n_quarters=str(len(data.quarters))))

    def update_report_date(self, new_report_date: ReportDate) -> None:
        previous_date = db.reference(company_path(new_report_date.ticker) + "/quarters/" + new_report_date.quarter + "/report_date_this_quarter").get()
        db.reference(company_path(new_report_date.ticker) + "/quarters/" + new_report_date.quarter + "/report_date_this_quarter").set(new_report_date.report_date)
        db.reference(company_path(new_report_date.ticker) + "/info/last_update").set(datetime.now().strftime("%Y-%m-%d"))
        self.log(LogMsg.REPORT_DATE_UPDATED.format(previous_date=previous_date, new_date=new_report_date.report_date, company_id=new_report_date.ticker, quarter_id=new_report_date.quarter))

    def report_quarter(self, company_id: str, quarter_data: Quarter) -> None:
        db.reference(company_path(company_id) + "/quarters/" + quarter_data.id).set(quarter_data.model_dump())
        db.reference(company_path(company_id) + "/info/last_update").set(datetime.now().strftime("%Y-%m-%d"))
        self.log(LogMsg.QUARTER_REPORTED.format(company_id=company_id, quarter_id=quarter_data.id, date=quarter_data.report_date_this_quarter))

    def create_quarter(self, company_id, new_quarter_data: Quarter) -> None:
        db.reference(company_path(company_id) + "/quarters/" + new_quarter_data.id).set(new_quarter_data.model_dump())
        db.reference(company_path(company_id) + "/info/current_quarter_id").set(new_quarter_data.id)
        db.reference(company_path(company_id) + "/info/last_update").set(datetime.now().strftime("%Y-%m-%d"))
        self.log(LogMsg.QUARTER_CREATED.format(company_id=company_id, quarter_id=new_quarter_data.id))

