import {restClient} from '@polygon.io/client-js';
import {polygon_api_key} from "../properties";

const rest = restClient(polygon_api_key);

export function getFinancial(ticker, year, term) {
    let timeframe = undefined
    if (term.startsWith("Q")) timeframe = "quarterly"
    if (term.startsWith("F")) timeframe = "annual"

    return rest.reference.stockFinancials({ticker: ticker, timeframe: timeframe})
        .then((data) => {
            let financial = data.results.find(result => (result.fiscal_period === term && result.fiscal_year === year))
            if (financial && financial.financials && financial.financials.income_statement) {
                return financial
            }
        }).catch(e => {
        console.error('An error happened:', e);
    });
}

export function getQuote(ticker, from, to){
    return rest.stocks.aggregates(ticker, 1, "day", from, to)
        .then(data => {
            if (!data.results || data.results.length === 0) {
                return null
            } else if (data.results.length === 1){
                return data.results[0];
            } else {
                let merged = data.results[0];
                merged.c = data.results[data.results.length - 1].c

                for (let i=0;i<data.results.length;i++){
                    if (Number(data.results[i].h) > Number(merged.h)) merged.h = data.results[i].h
                    if (Number(data.results[i].l) < Number(merged.l)) merged.l = data.results[i].l
                }
                return merged
            }

        }).catch(e => {
            console.error('An error happened:', e);
        });
}
