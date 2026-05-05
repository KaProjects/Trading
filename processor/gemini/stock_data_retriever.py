import calendar
from datetime import datetime, timedelta

import utils
from discord.discord_client import DiscordClient
from gemini.client import GeminiClient
from gemini.models import Company, ReportDate, ReportDates, Quarter
from gemini.service import FirebaseService
from gemini.strings import ErrorMsg
from utils import BaseClass


class StockDataRetrieverRunner(BaseClass):
    name = "StockDataRetriever"
    # model = "gemini-3-flash-preview"
    model = "gemini-3.1-pro-preview"

    def __init__(self, gemini_api_key, discord_webhook_key, **kwargs):
        super().__init__(identity=self.name, **kwargs)
        self.client = GeminiClient(api_key=gemini_api_key, parent=self.name, model=self.model, **kwargs)
        self.service = FirebaseService(parent=self.name, **kwargs)
        self.discord = DiscordClient(webhook_key=discord_webhook_key, parent=self.name, **kwargs)

    def run(self):
        try:
            companies: dict = self.service.get_companies()
            report_dates = ReportDates(report_dates=list())
            for company_id in companies:
                if companies.get(company_id) is None:
                    company: Company = self.client.get_initial_stock_data(company_id)
                    self.service.init_company(id=company_id, data=company)
                else:
                    company = companies.get(company_id)
                    current_quarter: Quarter = company.quarters.get(company.info.current_quarter_id)
                    if current_quarter is None:
                        self.log.error(ErrorMsg.QUARTER_NOT_FOUND.format(quarter_id=company.info.current_quarter_id, company_id=company_id))
                    else:
                        if utils.is_past_date(date=current_quarter.report_date_this_quarter):
                            current_quarter_reported: Quarter = self.client.get_quarter_report(company_id, current_quarter)
                            if current_quarter == current_quarter_reported:
                                self.log.error(ErrorMsg.QUARTER_REPORT_FAILED.format(quarter_id=company.info.current_quarter_id, company_id=company_id))
                                # might be rescheduled
                                report_date = ReportDate(ticker=company_id, quarter=current_quarter.id, report_date=current_quarter.report_date_this_quarter)
                                if datetime.now().weekday() == 6:
                                    report_dates.report_dates.append(report_date)
                                else:
                                    new_report_dates = self.client.revalidate_report_dates(ReportDates(report_dates=[report_date]))
                                    self.service.update_report_date(new_report_dates.report_dates.pop())
                            else:
                                self.service.report_quarter(company_id, current_quarter_reported)
                                new_quarter: Quarter = self.compose_new_quarter(current_quarter_reported)
                                self.service.create_quarter(company_id, new_quarter)
                                self.discord.post(self.format_quarter_for_discord(quarter=current_quarter_reported, ticker=company_id))
                        else:
                            report_dates.report_dates.append(ReportDate(ticker=company_id, quarter=current_quarter.id, report_date=current_quarter.report_date_this_quarter))

            if datetime.now().weekday() == 6:
                new_report_dates = self.client.revalidate_report_dates(report_dates)

                for index, report_date in enumerate(report_dates.report_dates):
                    new_report_date = new_report_dates.report_dates[index]
                    if report_date.report_date != new_report_date.report_date:
                        self.service.update_report_date(new_report_date)

                self.check_report_dates_next_week(new_report_dates)

        except Exception as exception:
            self.log.exception(exception)

    def create_discord_post_payload(self, embeds):
        return {
            "username": "Quarterly Results Reporter",
            "avatar_url": "https://cdn-icons-png.flaticon.com/512/1390/1390704.png",  # Optional
            "embeds": embeds
        }

    def format_quarter_for_discord(self, quarter: Quarter, ticker: str):
        return self.create_discord_post_payload([
                {
                    "title": f"{ticker} - {quarter.name} report",
                    "description": f"ending: {quarter.ending_month} | reported: {quarter.report_date_this_quarter}",
                    "color": 3066993,
                    "fields": [
                        {
                            "name": "Financials",
                            "value": (
                                f"**Revenues:** {self.format_financial(quarter.reported_revenues)}\n"
                                f"**Gross Profit:** {self.format_financial(quarter.reported_gross_profit)}\n"
                                f"**Oper. Income:** {self.format_financial(quarter.reported_operating_income)}\n"
                                f"**Net Income:** {self.format_financial(quarter.reported_net_income)}\n"
                                f"**Divs:** {self.format_financial(quarter.reported_div)}\n"
                                f"**Shares:** {self.format_financial(quarter.reported_shares)}\n"
                                f"**EPS:** {quarter.reported_eps}"
                            ),
                            "inline": False
                        },
                        {
                            "name": "Price Range (from previous report)",
                            "value": f"Low: **${quarter.price_min}** — High: **${quarter.price_max}**",
                            "inline": False
                        }
                    ]
                }
            ])

    def format_financial(self, original: str):
        if original is None: return ""
        try:
            result = float(original)
            if result == 0: return "-"
            if result > 1000:
                return str(round(result / 1000, 2)) + "B"
            else:
                return str(round(result, 2)) + "M"
        except (ValueError, TypeError):
            return ""

    def compose_new_quarter(self, previous_quarter: Quarter) -> Quarter:
        previous_y = int(previous_quarter.id[:2])
        previous_q = int(previous_quarter.id[3])
        previous_m = int(previous_quarter.ending_month[3:])

        if previous_q == 4:
            next_q = 1
            next_y = previous_y + 1
        else:
            next_q = previous_q + 1
            next_y = previous_y

        next_m = previous_m + 3
        if next_m > 12:
            next_m -= 12

        yy_str = f"{next_y:02d}"
        mm_str = f"{next_m:02d}"

        return Quarter(name=f"Q{next_q} 20{yy_str}", ending_month=f"{yy_str}-{mm_str}", id=f"{yy_str}Q{next_q}", report_date_previous_quarter=previous_quarter.report_date_this_quarter)

    def check_report_dates_next_week(self, report_dates: ReportDates):
        today = datetime.now().date()
        if today.weekday() != 6:
            self.log.error(ErrorMsg.SHOULD_RUN_ON_SUNDAY.format(today=calendar.day_name[today.weekday()]))
        else:
            report_map = {}
            for i in range(1, 6):
                weekday_date = (today + timedelta(days=i)).strftime("%Y-%m-%d")
                report_map[weekday_date] = []

            for report in report_dates.report_dates:
                if report.report_date in report_map:
                    info = f"{report.ticker} - {report.quarter}"
                    report_map[report.report_date].append(info)

            fields = list()
            for day in sorted(report_map.keys()):
                fields.append({
                            "name": f"**{datetime.strptime(day, "%Y-%m-%d").strftime("%A")}** ({day})",
                            "value": f"{"\n".join(report_map[day])}",
                            "inline": False
                        })

            self.discord.post(self.create_discord_post_payload([
                {
                    "title": "📅 Upcoming Earnings Reports",
                    "color": 3447003,
                    "fields": fields
                }
            ]))
