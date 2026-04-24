import traceback

from gemini.client import GeminiClient
from gemini.models import Company
from gemini.service import FirebaseService
from gemini.strings import LogMessage
from utils import BaseClass


class StockDataRetrieverRunner(BaseClass):
    context = {
        "verbose": False,
        "identity": "StockDataRetriever",
        # "model": "gemini-3-flash-preview",
        "model":"gemini-3.1-pro-preview",
    }

    def __init__(self, gemini_api_key):
        super().__init__(**self.context)
        self.client = GeminiClient(gemini_api_key=gemini_api_key, **self.context)
        self.service = FirebaseService(**self.context)

    def run(self):
        try:
            companies: dict = self.service.get_companies()
            for company_id in companies:
                if companies.get(company_id) is None:
                    company: Company = self.client.get_initial_stock_data(company_id)
                    self.service.init_company(id=company_id, data=company)
                    self.log(LogMessage.COMPANY_INIT.format(company_id=company_id, quarter_id=company.info.current_quarter_id, n_quarters=str(len(company.quarters))))
                else:
                    pass

        except Exception:
            self.log(traceback.format_exc())
            self.log("^^^ exception occurred!")
