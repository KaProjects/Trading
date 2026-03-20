import json

import finnhub

import utils

envs = utils.parse("../envs.json")
finnhub_client = finnhub.Client(api_key=envs["finnhub_api_key"])

if __name__ == "__main__":
    # print(finnhub_client.quote('SMCI'))
    # print(finnhub_client.company_peers('MSFT'))
    print(json.dumps(finnhub_client.stock_insider_transactions('MVST', '2026-01-01', '2027-01-01')))
