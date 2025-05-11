import asyncio

import fear_and_greed_discord
import tradingview_alert_processor
import utils


async def gather():
    await asyncio.gather(
        fear_and_greed_discord.run(envs),
        tradingview_alert_processor.run()
    )


if __name__ == '__main__':
    envs = utils.parse("envs.json")
    utils.init_firebase(envs["firebase"])
    asyncio.run(gather())


