from datetime import date

import finnhub

from myfinnhub.models import Earnings
from myfinnhub.strings import ErrMsg
from utils import BaseClass


class FinnhubClient(BaseClass):
    def __init__(self, api_key, parent, **kwargs):
        super().__init__(identity=parent+".FinnhubClient", **kwargs)
        self.client = finnhub.Client(api_key=api_key)
        self.client.DEFAULT_TIMEOUT = 30

    def get_earnings(self, company_id: str) -> dict[str, Earnings]:
        one_year_more = date.today().replace(year=date.today().year + 1, day=1)
        one_year_less = date.today().replace(year=date.today().year - 1, day=1)
        response = self.client.earnings_calendar(
            _from=one_year_less.strftime('%Y-%m-%d'),
            to=one_year_more.strftime('%Y-%m-%d'),
            symbol=company_id,
            international=False)

        quarters: dict[str, Earnings] = dict()

        if len(response["earningsCalendar"]) == 0:
            self.log.error(ErrMsg.NO_EARNINGS_FOUND.format(company_id=company_id))
        else:
            for earnings in response["earningsCalendar"]:
                quarter = str(earnings["year"])[2:] + "Q" + str(earnings["quarter"])
                data = {"epse": earnings["epsEstimate"], "epsa": earnings["epsActual"],
                        "reve": earnings["revenueEstimate"], "reva": earnings["revenueActual"],
                        "report": earnings["date"] + "-" + earnings["hour"]}
                quarters[quarter] = Earnings.model_validate(data)

        return quarters
