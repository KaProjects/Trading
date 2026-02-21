import asyncio

import schedule

import tradingview_alert_processor
import utils
from fear_and_greed_discord import BtcFngDiscordRunner
from finnhub_earnings import FinnhubEarningsRunner


async def gather():
    await asyncio.gather(
        cron(),
        tradingview_alert_processor.run()
    )

async def cron():
    btc_fng_discord_runner = BtcFngDiscordRunner(envs["discord_btc_webhook_key"], envs["cmc_api_key"])
    finnhub_earnings_runner = FinnhubEarningsRunner(envs["finnhub_api_key"], envs["discord_eventlog_webhook_key"])

    # schedule.every(5).seconds.do(btc_fng_discord_runner.run)
    schedule.every().day.at("03:00").do(btc_fng_discord_runner.run)
    schedule.every().day.at("07:00").do(finnhub_earnings_runner.run)

    while True:
        schedule.run_pending()
        await asyncio.sleep(60)

if __name__ == '__main__':
    envs = utils.parse("envs.json")
    utils.init_firebase(envs["firebase"])
    asyncio.run(gather())


