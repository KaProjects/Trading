package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Dividend;
import org.kaleta.entity.Trade;
import org.kaleta.model.StatsByCompany;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.kaleta.Utils.format;

@ApplicationScoped
public class StatsService
{
    @Inject
    TradeService tradeService;
    @Inject
    DividendService dividendService;

    public List<StatsByCompany> getByCompany(String currency)
    {
        Map<String, StatsByCompany> companyMap = new HashMap<>();
        List<Trade> trades = tradeService.getTrades(false, null, currency, null);
        for (Trade trade : trades)
        {
            if (!companyMap.containsKey(trade.getTicker())) {
                companyMap.put(trade.getTicker(), new StatsByCompany(trade.getTicker(), trade.getCurrency()));
            }
            companyMap.get(trade.getTicker()).addPurchase(trade.getPurchaseTotal());
            companyMap.get(trade.getTicker()).addSell(trade.getSellTotal());
        }
        List<Dividend> dividends = dividendService.getDividends(null , currency, null);
        for (Dividend dividend : dividends)
        {
            if (!companyMap.containsKey(dividend.getTicker())) {
                companyMap.put(dividend.getTicker(), new StatsByCompany(dividend.getTicker(), dividend.getCurrency()));
            }
            companyMap.get(dividend.getTicker()).addDividend(dividend.getTotal());
        }
        List<StatsByCompany> companyStats = new ArrayList<>(companyMap.values());
        companyStats.sort(StatsByCompany::compareProfitTo);
        return companyStats;
    }

    public String[] computeSums(List<StatsByCompany> companyStats)
    {
        Set<String> companies = new HashSet<>();
        Set<Currency> currencies = new HashSet<>();
        StatsByCompany sumStats = new StatsByCompany();
        for (StatsByCompany stats : companyStats)
        {
            companies.add(stats.getTicker());
            currencies.add(stats.getCurrency());
            sumStats.addPurchase(stats.getPurchaseSum());
            sumStats.addSell(stats.getSellSum());
            sumStats.addDividend(stats.getDividendSum());
        }
        return new String[]{String.valueOf(companies.size()), String.valueOf(currencies.size()),
                format(sumStats.getPurchaseSum()), format(sumStats.getSellSum()), format(sumStats.getDividendSum()),
                format(sumStats.getProfit()), format(sumStats.getProfitPercentage())};
    }
}
