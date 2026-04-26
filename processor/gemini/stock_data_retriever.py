import traceback
from datetime import datetime

import utils
from gemini.client import GeminiClient
from gemini.models import Company, ReportDate, ReportDates
from gemini.service import FirebaseService
from gemini.strings import LogMsg, ErrorMsg
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
            report_dates = ReportDates(report_dates=list())
            for company_id in companies:
                if companies.get(company_id) is None:
                    company: Company = self.client.get_initial_stock_data(company_id)
                    self.service.init_company(id=company_id, data=company)
                    self.log(LogMsg.COMPANY_INIT.format(company_id=company_id, quarter_id=company.info.current_quarter_id, n_quarters=str(len(company.quarters))))
                else:
                    company = companies.get(company_id)
                    current_quarter = company.quarters.get(company.info.current_quarter_id)
                    if current_quarter is None:
                        raise Exception(ErrorMsg.QUARTER_NOT_FOUND.format(quarter_id=company.info.current_quarter_id, company_id=company_id))
                    else:
                        if utils.is_past_date(date_str=current_quarter.report_date_this_quarter):
                            # TODO when date is after report date: get report + new quarter
                            pass
                        else:
                            report_dates.report_dates.append(ReportDate(ticker=company_id, quarter=current_quarter.id, report_date=current_quarter.report_date_this_quarter))

            if datetime.now().weekday() == 6:
                new_report_dates = self.client.revalidate_report_dates(report_dates)

                for index, report_date in enumerate(report_dates.report_dates):
                    new_report_date = new_report_dates.report_dates[index]
                    if report_date.report_date != new_report_date.report_date:
                        self.service.update_report_date(new_report_date)
                        self.log(LogMsg.REPORT_DATE_UPDATED.format(previous_date=report_date.report_date, new_date=new_report_date.report_date, company_id=report_date.ticker, quarter_id=report_date.quarter))


        except Exception:
            self.log(traceback.format_exc())
            self.log("^^^ exception occurred!")
