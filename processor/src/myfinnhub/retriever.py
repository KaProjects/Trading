import logging
import time
from collections.abc import Callable
from decimal import Decimal, InvalidOperation

from discord.client import DiscordChannel, DiscordClient
from error_reporting import ErrorReporter
from myfinnhub.client import FinnhubClient
from myfinnhub.models import Company, Earnings
from myfinnhub.service import FirebaseService
from myfinnhub.strings import ErrMsg, LogMsg

RUNNER_NAME = "FinnhubEarnings"
logger = logging.getLogger(RUNNER_NAME)
RELATIVE_TOLERANCE = Decimal("0.05")
ABSOLUTE_TOLERANCE = Decimal("0.01")


class FinnhubEarningsRetrieverRunner:
    log = logger
    name = RUNNER_NAME

    def __init__(
        self,
        finnhub_api_key: str | None = None,
        client: FinnhubClient | None = None,
        service: FirebaseService | None = None,
        discord: DiscordClient | None = None,
        error_reporter: ErrorReporter | None = None,
        sleeper: Callable[[float], None] = time.sleep,
    ) -> None:
        if client is None:
            if finnhub_api_key is None:
                raise ValueError("finnhub_api_key is required without a client")
            client = FinnhubClient(api_key=finnhub_api_key)
        if discord is None:
            raise ValueError("discord is required")

        self.errors = error_reporter or ErrorReporter(environment="local")
        self.client = client
        self.service = (
            service
            if service is not None
            else FirebaseService(error_reporter=self.errors)
        )
        self.discord = discord
        self.sleeper = sleeper

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

                    self.sleeper(5)
                except Exception as exception:
                    self.log.error(ErrMsg.ERROR_PROCESSING_COMPANY.format(company_id=company_id))
                    self.errors.report(
                        exception,
                        logger=self.log,
                        source=self.name,
                        operation="process_company",
                        context={"company_id": company_id},
                    )
        except Exception as exception:
            self.errors.report(
                exception,
                logger=self.log,
                source=self.name,
                operation="run",
            )

    def discord_post_earnings(
        self,
        ticker: str,
        quarter: str,
        latest: Earnings | None,
        now: Earnings,
    ) -> None:
        epse = self.format_eps(now.epse)
        if latest is not None and not self.almost_equals(latest.epse, now.epse):
            epse = self.format_eps(latest.epse) + " -> " + epse

        reve = self.format_revenue(now.reve)
        if latest is not None and not self.almost_equals(latest.reve, now.reve):
            reve = self.format_revenue(latest.reve) + " -> " + reve

        fields = list()
        fields.append({"name": "Estimates:", "value": f"earnings: \u200b {epse}\nrevenues: \u200b {reve}"})

        if now.epsa is not None or now.reva is not None:
            epsa = self.format_eps(now.epsa)
            if latest is not None and not self.almost_equals(latest.epsa, now.epsa):
                epsa = self.format_eps(latest.epsa) + " -> " + epsa

            reva = self.format_revenue(now.reva)
            if latest is not None and not self.almost_equals(latest.reva, now.reva):
                reva = self.format_revenue(latest.reva) + " -> " + reva

            fields.append({"name": "Reported:", "value": f"earnings: \u200b {epsa}\nrevenues: \u200b {reva}"})

        self.discord.post(
            DiscordChannel.EVENTLOG,
            self.create_discord_post_payload([{
                "title": f"📊 {ticker} | {quarter} | {now.report}",
                "color": 0x3498db,
                "fields": fields
            }]),
        )

    def format_revenue(self, original: Decimal | None) -> str:
        if original is None: return ""
        result = original / 1000000
        if result > 1000:
            return str(round(result / 1000, 2)) + "B"
        else:
            return str(round(result, 2)) + "M"

    def format_eps(self, original: Decimal | None) -> str:
        if original is None: return ""
        return str(round(original, 2))

    def almost_equals_earnings(self, a: Earnings, b: Earnings) -> bool:
        return self.almost_equals(a.epse, b.epse) and self.almost_equals(a.reve, b.reve) and self.almost_equals(a.epsa, b.epsa) and self.almost_equals(a.reva, b.reva)

    def almost_equals(self, a: object, b: object) -> bool:
        if a is None or b is None:
            return a is None and b is None

        try:
            left = Decimal(str(a))
            right = Decimal(str(b))
        except (InvalidOperation, TypeError, ValueError):
            return False

        if not left.is_finite() or not right.is_finite():
            return False

        difference = abs(left - right)
        tolerance = max(
            ABSOLUTE_TOLERANCE,
            RELATIVE_TOLERANCE * max(abs(left), abs(right)),
        )
        return difference <= tolerance

    def create_discord_post_payload(self, embeds):
        return {
            "embeds": embeds
        }
