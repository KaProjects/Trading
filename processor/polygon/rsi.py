from datetime import datetime

from polygon import RESTClient

import utils

envs = utils.parse("../envs.json")
client = RESTClient(envs["polygon_api_key"])


if __name__ == "__main__":
	print(datetime.now().timestamp())
	rsi = client.get_rsi(
		ticker="NVDA",
		timespan="hour",
		adjusted="true",
		window="14",
		series_type="close",
		order="desc",
		limit=5000
	)

	print(rsi.values)
	for value in rsi.values:
		if 20 > value.value: print(value)