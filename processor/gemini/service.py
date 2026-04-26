from firebase_admin import db
from pydantic import BaseModel

from gemini.models import Company, ReportDate
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

    def init_company(self, id: str, data: BaseModel) -> None:
        db.reference(company_path(id)).set(data.model_dump())

    def update_report_date(self, new_report_date: ReportDate) -> None:
        db.reference(company_path(new_report_date.ticker) + "/quarters/" + new_report_date.quarter + "/report_date_this_quarter").set(new_report_date.report_date)
