import json
import time
import traceback
from datetime import date

from firebase_admin import db
from requests import Session
from requests.exceptions import ConnectionError, Timeout, TooManyRedirects

import finnhub
import utils

company_path = "company/"

def log(message: str):
    utils.log("FHE", message)


class FinnhubEarningsRunner:
    def __init__(self, finnhub_api_key, discord_webhook_key):
        self.client = finnhub.Client(api_key=finnhub_api_key)
        self.client.DEFAULT_TIMEOUT = 30
        self.discord_webhook_key = discord_webhook_key

    def run(self):
        try:
            companies: dict = db.reference(company_path).get()
            for company_id in companies:
                try:
                    quarters = dict()
                    response = self.earnings_request(company_id)
                    if len(response["earningsCalendar"]) == 0:
                        log(company_id + " found no earnings")
                        continue
                    for earnings in response["earningsCalendar"]:
                        quarter = str(earnings["year"])[2:] + "Q" + str(earnings["quarter"])
                        today = date.today().strftime('%Y%m%d')
                        data = {"epse": earnings["epsEstimate"], "epsa": earnings["epsActual"],
                                "reve": earnings["revenueEstimate"], "reva": earnings["revenueActual"],
                                "report": earnings["date"] + "-" + earnings["hour"]}
                        quarters[quarter] = {today: data}

                    if not isinstance(companies[company_id], dict):
                        log(company_id + " initiated with " + str(len(quarters)) + " quarters")
                        db.reference(company_path + "/" + company_id).set({"fhe": quarters})
                        for quarter in quarters.__reversed__():
                            self.discord_post_earnings(company_id, quarter, dict(), quarters[quarter][today])
                    else:
                        for quarter in quarters.__reversed__():
                            if quarter not in companies[company_id]["fhe"]:
                                log(company_id + " found new quarter: " + quarter)
                                db.reference(company_path + "/" + company_id + "/fhe/" + quarter).set(quarters[quarter])
                                self.discord_post_earnings(company_id, quarter, dict(), quarters[quarter][today])
                            else:
                                latest = companies[company_id]["fhe"][quarter][max(companies[company_id]["fhe"][quarter])]
                                now = quarters[quarter][today]

                                if latest["epse"] != now["epse"] or latest["reve"] != now["reve"] or latest.get("epsa") != now["epsa"] or latest.get("reva") != now["reva"]:
                                    log(company_id + " change detected for quarter: " + quarter)
                                    db.reference(company_path + "/" + company_id + "/fhe/" + quarter + "/" + today).set(quarters[quarter][today])
                                    self.discord_post_earnings(company_id, quarter, latest, now)

                    time.sleep(5)
                except Exception:
                    log(traceback.format_exc())
                    log("^^^ exception occurred!")
        except Exception:
            log(traceback.format_exc())
            log("^^^ exception occurred!")

    def earnings_request(self, ticker: str):
        today = date.today()
        one_year_more = self.adjust_year(today, 1)
        one_year_less = self.adjust_year(today, -1)
        return self.client.earnings_calendar(
            _from=one_year_less.strftime('%Y-%m-%d'),
            to=one_year_more.strftime('%Y-%m-%d'),
            symbol=ticker,
            international=False)

    def adjust_year(self, base_date, years_to_add):
        try:
            return base_date.replace(year=base_date.year + years_to_add)
        except ValueError:
            return base_date.replace(year=base_date.year + years_to_add, month=2, day=28)

    def discord_post(self, payload: object):
        url = "https://discord.com/api/webhooks/" + self.discord_webhook_key
        headers = {"Content-Type": "application/json"}

        session = Session()
        session.headers.update(headers)

        try:
            response = session.post(url, data=json.dumps(payload))
            if response.status_code != 204:
                log(response.text)
        except (ConnectionError, Timeout, TooManyRedirects) as e:
            log("Error: " + str(e))

    def discord_post_earnings(self, ticker, quarter, latest, now):
        eps = self.format_eps(latest.get("epse"))
        if latest.get("epse") != now["epse"]:
            eps += " -> " + self.format_eps(now["epse"])

        revenue = self.format_revenue(latest.get("reve"))
        if latest.get("reve") != now["reve"]:
            revenue += " -> " + self.format_revenue(now["reve"])

        reported = ["", "", ""]
        if now["epsa"] and now["reva"]:
            reported[0] = "\nreported:"
            reported[1] = "\n" + self.format_eps(latest.get("epsa"))
            if latest.get("epsa") != now["epsa"]:
                reported[1] += " -> " + self.format_eps(now["epsa"])

            reported[2] = "\n" + self.format_revenue(latest.get("reva"))
            if latest.get("reva") != now["reva"]:
                reported[2] += " -> " + self.format_revenue(now["reva"])

        self.discord_post({"embeds": [
            {"fields": [
                {"name": "[" + ticker + " | " + quarter + " | " + now["report"] + "]", "value": ""},
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

