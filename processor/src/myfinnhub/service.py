import logging
from datetime import date

from firebase_admin import db

from firebase_repository import parse_company_snapshot
from myfinnhub.models import Company, Quarter, Earnings
from myfinnhub.strings import LogMsg

companies_path = "company"
data_root = "fhe"
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

    def init_company(self, company_id: str, earnings: dict[str, Earnings]):
        today = date.today().strftime('%Y%m%d')
        quarters: dict[str, Quarter] = dict()

        for quarter_id in earnings:
            quarters[quarter_id] = Quarter.model_validate({today: earnings[quarter_id]})

        company = Company.model_validate(quarters)
        db.reference(company_path(company_id)).set(company.model_dump(mode="json"))
        self.log.info(LogMsg.COMPANY_INIT.format(company_id=company_id, n_quarters=str(len(quarters))))

    def init_quarter(self, company_id, quarter_id, earnings: Earnings):
        today = date.today().strftime('%Y%m%d')
        db.reference(company_path(company_id) + "/" + quarter_id).set({
            today: earnings.model_dump(mode="json")
        })
        self.log.info(LogMsg.QUARTER_INIT.format(company_id=company_id, quarter_id=quarter_id))

    def new_earnings(self, company_id, quarter_id, earnings: Earnings):
        today = date.today().strftime('%Y%m%d')
        db.reference(company_path(company_id) + "/" + quarter_id + "/" + today).set(
            earnings.model_dump(mode="json")
        )
        self.log.info(LogMsg.NEW_EARNINGS.format(company_id=company_id, quarter_id=quarter_id))
