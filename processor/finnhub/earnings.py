import finnhub

import utils

envs = utils.parse("../envs.json")
finnhub_client = finnhub.Client(api_key=envs["finnhub_api_key"])

if __name__ == "__main__":
    response = finnhub_client.earnings_calendar(_from="2025-01-01", to="2025-05-10", symbol="", international=False)
    # print(response)
    for earnings in response["earningsCalendar"]:
        print(earnings["symbol"])
        if earnings["symbol"] == "SMCI":
            print(earnings)

