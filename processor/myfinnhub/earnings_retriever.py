import math
import time

from discord.discord_client import DiscordClient
from myfinnhub.client import FinnhubClient
from myfinnhub.models import Company, Earnings
from myfinnhub.service import FirebaseService
from myfinnhub.strings import ErrMsg, LogMsg
from utils import BaseClass


class FinnhubEarningsRetrieverRunner(BaseClass):
    name = "FinnhubEarnings"

    def __init__(self, finnhub_api_key, discord_webhook_key, **kwargs):
        super().__init__(identity=self.name, **kwargs)
        self.client = FinnhubClient(api_key=finnhub_api_key, parent=self.name, **kwargs)
        self.service = FirebaseService(parent=self.name, **kwargs)
        self.discord = DiscordClient(webhook_key=discord_webhook_key, parent=self.name, **kwargs)

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

                                if not self.almost_equals_earnings(latest, now):
                                    no_change = False
                                    self.service.new_earnings(company_id, quarter_id, earnings[quarter_id])
                                    self.discord_post_earnings(company_id, quarter_id, latest, now)
                        if no_change:
                            self.log.info(LogMsg.NO_CHANGE.format(company_id=company_id))

                    time.sleep(5)
                except Exception as exception:
                    self.log.error(ErrMsg.ERROR_PROCESSING_COMPANY.format(company_id=company_id))
                    self.log.exception(exception)
        except Exception as exception:
            self.log.exception(exception)

    def discord_post_earnings(self, ticker, quarter, latest: Earnings, now: Earnings):
        if latest is None: latest = Earnings(epse=None, reve=None, report="")
        epse = self.format_eps(now.epse)
        if not self.almost_equals(latest.epse, now.epse):
            epse = self.format_eps(latest.epse) + " -> " + epse

        reve = self.format_revenue(now.reve)
        if not self.almost_equals(latest.reve, now.reve):
            reve = self.format_revenue(latest.reve) + " -> " + reve

        fields = list()
        fields.append({"name": "Estimates:", "value": f"earnings: \u200b {epse}\nrevenues: \u200b {reve}"})

        if now.epsa and now.reva:
            epsa = self.format_eps(now.epsa)
            if not self.almost_equals(latest.epsa, now.epsa):
                epsa = self.format_eps(latest.epsa) + " -> " + epsa

            reva = self.format_revenue(now.reva)
            if not self.almost_equals(latest.reva, now.reva):
                reva = self.format_revenue(latest.reva) + " -> " + reva

            fields.append({"name": "Reported:", "value": f"earnings: \u200b {epsa}\nrevenues: \u200b {reva}"})

        self.discord.post(self.create_discord_post_payload([{
            "title": f"📊 {ticker} | {quarter} | {now.report}",
            "color": 0x3498db,
            "fields": fields
        }]))

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

    def almost_equals_earnings(self, a: Earnings, b: Earnings):
        return self.almost_equals(a.epse, b.epse) and self.almost_equals(a.reve, b.reve) and self.almost_equals(a.epsa, b.epsa) and self.almost_equals(a.reva, b.reva)

    def almost_equals(self, a, b):
        if a is None or not isinstance(a, (int, float)): a = -1000
        if b is None or not isinstance(b, (int, float)): b = -1000
        return math.isclose(a, b, rel_tol=0.05)

    def create_discord_post_payload(self, embeds):
        return {
            "username": "Earnings Estimates Reporter",
            "avatar_url": "https://cdn-icons-png.flaticon.com/512/1353/1353566.png",  # Optional
            "embeds": embeds
        }