import time
import traceback

import utils
from discord.discord_client import DiscordClient
from myfinnhub.client import FinnhubClient
from myfinnhub.models import Company, Earnings
from myfinnhub.service import FirebaseService
from myfinnhub.strings import ErrorMsg, LogMsg
from utils import BaseClass

company_path = "company/"

def log(message: str):
    utils.log("FHE", message)

data_root = "fhe"

class FinnhubEarningsRunner(BaseClass):
    context = {
        "verbose": False,
        "identity": "FinnhubEarnings",
    }

    def __init__(self, finnhub_api_key, discord_webhook_key):
        super().__init__(**self.context)
        self.client = FinnhubClient(api_key=finnhub_api_key, **self.context)
        self.service = FirebaseService(**self.context)
        self.discord = DiscordClient(webhook_key=discord_webhook_key, **self.context)

    def run(self):
        try:
            companies: dict[str, Company] = self.service.get_companies()
            for company_id in companies:
                try:
                    earnings: dict[str, Earnings] = self.client.get_earnings(company_id)
                    if len(earnings) == 0: continue
                    if companies.get(company_id) is None:
                        self.service.init_company(company_id, earnings)
                        for quarter_id in earnings.__reversed__():
                            self.discord_post_earnings(company_id, quarter_id, None, earnings[quarter_id])
                    else:
                        no_change = True
                        for quarter_id in earnings.__reversed__():
                            if quarter_id not in companies[company_id].root:
                                no_change = False
                                self.service.init_quarter(company_id, quarter_id, earnings[quarter_id])
                                self.discord_post_earnings(company_id, quarter_id, None, earnings[quarter_id])
                            else:
                                latest = companies[company_id].root[quarter_id].root[max(companies[company_id].root[quarter_id].root)]
                                now = earnings[quarter_id]

                                if not self.strict_equals_earnings(latest, now):
                                    no_change = False
                                    self.service.new_earnings(company_id, quarter_id, earnings[quarter_id])
                                    if not self.almost_equals_earnings(latest, now):
                                        self.discord_post_earnings(company_id, quarter_id, latest, now)
                        if no_change:
                            self.log(LogMsg.NO_CHANGE.format(company_id=company_id))

                    time.sleep(5)
                except Exception:
                    self.log(ErrorMsg.ERROR_PROCESSING_COMPANY.format(company_id=company_id, trace=traceback.format_exc()))
        except Exception:
            self.log(traceback.format_exc() + "\n^^^ exception occurred!")

    def discord_post_earnings(self, ticker, quarter, latest: Earnings, now: Earnings):
        if latest is None:
            latest = Earnings(epse=-1, reve=-1, report="")
        # TODO improve design here
        eps = self.format_eps(latest.epse)
        if latest.epse != now.epse:
            eps += " -> " + self.format_eps(now.epse)

        revenue = self.format_revenue(latest.reve)
        if latest.reve != now.reve:
            revenue += " -> " + self.format_revenue(now.reve)

        reported = ["", "", ""]
        if now.epsa and now.reva:
            reported[0] = "\nreported:"
            reported[1] = "\n" + self.format_eps(latest.epsa)
            if latest.epsa != now.epsa:
                reported[1] += " -> " + self.format_eps(now.epsa)

            reported[2] = "\n" + self.format_revenue(latest.reva)
            if latest.reva != now.reva:
                reported[2] += " -> " + self.format_revenue(now.reva)

        self.discord.post({"embeds": [
            {"fields": [
                {"name": "[" + ticker + " | " + quarter + " | " + now.report + "]", "value": ""},
                {"name": ".", "value": "estimate:" + reported[0], "inline": "true"},
                {"name": "eps", "value": eps + reported[1], "inline": "true"},
                {"name": "revenue", "value": revenue + reported[2], "inline": "true"},
            ]}
        ]})

    def format_revenue(self, original):
        if original is None: return ""
        result = original / 1000000
        if result > 1000:
            return str(round(result / 1000, 2)) + "B"
        else:
            return str(round(result, 2)) + "M"

    def format_eps(self, original):
        if original is None: return ""
        return str(round(original, 2))

    def strict_equals_earnings(self, a: Earnings, b: Earnings):
        return a.epse == b.epse and a.reve == b.reve and a.epsa == b.epsa and a.reva == b.reva

    def almost_equals_earnings(self, a: Earnings, b: Earnings):
        # TODO compare rounded
        return a.epse == b.epse and a.reve == b.reve and a.epsa == b.epsa and a.reva == b.reva
