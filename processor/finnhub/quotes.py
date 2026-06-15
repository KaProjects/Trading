import finnhub

import utils

envs = utils.parse("../envs.json")
finnhub_client = finnhub.Client(api_key=envs["finnhub_api_key"])

if __name__ == "__main__":
    print(finnhub_client.quote('NVDA'))