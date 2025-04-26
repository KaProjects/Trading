import {restClient} from '@polygon.io/client-js';
import {polygon_api_key} from "../properties";


const rest = restClient(polygon_api_key);

export function getFinancial(ticker, year, period) {
    let timeframe = undefined
    if (period.startsWith("Q")) timeframe = "quarterly"
    if (period.startsWith("F")) timeframe = "annual"

    return rest.reference.stockFinancials({ticker: ticker, timeframe: timeframe})
        .then((data) => {
            let financial = data.results.find(result => (result.fiscal_period === period && result.fiscal_year === year))
            if (financial && financial.financials && financial.financials.income_statement) {
                return financial
            }
        }).catch(e => {
        console.error('An error happened:', e);
    });
}
