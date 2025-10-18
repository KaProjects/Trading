import asyncio
import schedule
import tradingview_alert_processor
import utils

from fear_and_greed_discord import BtcFngDiscordRunner

async def gather():
    await asyncio.gather(
        cron(),
        tradingview_alert_processor.run()
    )

async def cron():
    btc_fng_discord_runner = BtcFngDiscordRunner(envs["discord_api_key"], envs["cmc_api_key"])

    # schedule.every(5).seconds.do(btc_fng_discord_runner.run)
    schedule.every().day.at("05:00").do(btc_fng_discord_runner.run)

    while True:
        schedule.run_pending()
        await asyncio.sleep(60)

if __name__ == '__main__':
    envs = utils.parse("envs.json")
    utils.init_firebase(envs["firebase"])
    asyncio.run(gather())


