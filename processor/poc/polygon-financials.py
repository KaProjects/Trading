from polygon import RESTClient

import utils

envs = utils.parse("../envs.json")
client = RESTClient(envs["polygon_api_key"])



if __name__ == "__main__":
    aaa = client.vx.list_stock_financials(ticker='NVDA', timeframe='quarterly', filing_date_gt='2020-01-01')
    for f in aaa:
        print(f.end_date)
        print(f.financials.income_statement.revenues)