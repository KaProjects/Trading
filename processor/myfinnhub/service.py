from datetime import date

from firebase_admin import db

from myfinnhub.models import Company, Quarter, Earnings
from myfinnhub.strings import LogMsg
from utils import BaseClass

companies_path = "company"
data_root = "fhe"

def company_path(company_id: str) -> str:
    return companies_path + "/" + company_id + "/" + data_root

class FirebaseService(BaseClass):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)

    def get_companies(self) -> dict[str, Company]:
        companies_data: dict = db.reference(companies_path).get()
        companies: dict[str, Company] = dict()
        for company_id in companies_data:
            if not isinstance(companies_data[company_id], dict) or companies_data[company_id].get(data_root) is None:
                companies[company_id] = None
            else:
                companies[company_id] = Company.model_validate(companies_data[company_id][data_root])
        return companies

    def init_company(self, company_id: str, earnings: dict[str, Earnings]):
        today = date.today().strftime('%Y%m%d')
        quarters: dict[str, Quarter] = dict()

        for quarter_id in earnings:
            quarters[quarter_id] = Quarter.model_validate({today: earnings[quarter_id]})

        company = Company.model_validate(quarters)
        db.reference(company_path(company_id)).set(company.model_dump())
        self.log(LogMsg.COMPANY_INIT.format(company_id=company_id, n_quarters=str(len(quarters))))

    def init_quarter(self, company_id, quarter_id, earnings: Earnings):
        today = date.today().strftime('%Y%m%d')
        db.reference(company_path(company_id) + "/" + quarter_id).set({today: earnings.model_dump()})
        self.log(LogMsg.QUARTER_INIT.format(company_id=company_id, quarter_id=quarter_id))

    def new_earnings(self, company_id, quarter_id, earnings: Earnings):
        today = date.today().strftime('%Y%m%d')
        db.reference(company_path(company_id) + "/" + quarter_id + "/" + today).set(earnings.model_dump())
        self.log(LogMsg.NEW_EARNINGS.format(company_id=company_id, quarter_id=quarter_id))
