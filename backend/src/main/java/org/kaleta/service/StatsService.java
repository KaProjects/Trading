package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.joda.time.DateTime;
import org.kaleta.Utils;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Dividend;
import org.kaleta.entity.Trade;
import org.kaleta.model.StatsByCompany;
import org.kaleta.model.StatsByMonth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.kaleta.Utils.format;

@ApplicationScoped
public class StatsService
{
    @Inject
    TradeService tradeService;
    @Inject
    DividendService dividendService;

    public List<StatsByCompany> getByCompany()
    {
        Map<String, StatsByCompany> companyMap = new HashMap<>();
        List<Trade> trades = tradeService.getTrades(false, null, null, null);
        for (Trade trade : trades)
        {
            if (!companyMap.containsKey(trade.getTicker())) {
                companyMap.put(trade.getTicker(), new StatsByCompany(trade.getTicker(), trade.getCurrency()));
            }
            companyMap.get(trade.getTicker()).addPurchase(trade.getPurchaseTotal());
            companyMap.get(trade.getTicker()).addSell(trade.getSellTotal());
        }
        List<Dividend> dividends = dividendService.getDividends(null , null, null);
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

    public String[] computeCompanySums(List<StatsByCompany> companyStats)
    {
        Set<String> companies = new HashSet<>();
        Set<Currency> currencies = new HashSet<>();
        StatsByCompany sumStats = new StatsByCompany();
        BigDecimal profitUsdSum = new BigDecimal(0);
        for (StatsByCompany stats : companyStats)
        {
            companies.add(stats.getTicker());
            currencies.add(stats.getCurrency());
            sumStats.addPurchase(stats.getPurchaseSum());
            sumStats.addSell(stats.getSellSum());
            sumStats.addDividend(stats.getDividendSum());
            profitUsdSum = profitUsdSum.add(stats.getProfitUsd());
        }
        return new String[]{String.valueOf(companies.size()), String.valueOf(currencies.size()),
                format(sumStats.getPurchaseSum()), format(sumStats.getSellSum()), format(sumStats.getDividendSum()),
                format(sumStats.getProfit()), format(profitUsdSum), format(sumStats.getProfitPercentage())};
    }

    public List<StatsByMonth> getByMonth()
    {
        Map<String, StatsByMonth> monthlyMap = new HashMap<>();
        List<Trade> trades = tradeService.getTrades(false, null, null, null);
        for (Trade trade : trades)
        {
            String month = Utils.format(trade.getSellDate()).substring(3);
            if (!monthlyMap.containsKey(month)) {
                monthlyMap.put(month, new StatsByMonth(month));
            }
            monthlyMap.get(month).addPurchase(trade.getPurchaseTotal().multiply(trade.getCurrency().toUsd()).setScale(2, RoundingMode.HALF_UP));
            monthlyMap.get(month).addSell(trade.getSellTotal().multiply(trade.getCurrency().toUsd()).setScale(2, RoundingMode.HALF_UP));
            monthlyMap.get(month).increaseTrade();
        }
        List<Dividend> dividends = dividendService.getDividends(null , null, null);
        for (Dividend dividend : dividends)
        {
            String month = Utils.format(dividend.getDate()).substring(3);
            if (!monthlyMap.containsKey(month)) {
                monthlyMap.put(month, new StatsByMonth(month));
            }
            monthlyMap.get(month).addDividend(dividend.getTotal().multiply(dividend.getCurrency().toUsd()).setScale(2, RoundingMode.HALF_UP));
        }
        Set<String> years = monthlyMap.values().stream().map(stat -> stat.getMonth().substring(3)).collect(Collectors.toSet());
        for (String year : years) {
            for (int i=1; i<=12; i++) {
                String month = String.format("%02d", i) + "." + year;
                if (!monthlyMap.containsKey(month) && (Integer.parseInt(year) < DateTime.now().getYear() || i <= DateTime.now().getMonthOfYear())){
                    monthlyMap.put(month, new StatsByMonth(month));
                }
            }
        }
        List<StatsByMonth> monthlyStats = new ArrayList<>(monthlyMap.values());
        monthlyStats.sort(StatsByMonth::compareMonthTo);
        return monthlyStats;
    }

    public String[] computeMonthlySums(List<StatsByMonth> monthlyStats)
    {
        StatsByMonth sumStats = new StatsByMonth();
        int tradesSum = 0;
        for (StatsByMonth stats : monthlyStats)
        {
            tradesSum += stats.getTradesCount();
            sumStats.addPurchase(stats.getPurchaseSum());
            sumStats.addSell(stats.getSellSum());
            sumStats.addDividend(stats.getDividendSum());
        }
        return new String[]{String.valueOf(monthlyStats.size()),
                String.valueOf(tradesSum), format(sumStats.getTradesProfit()), format(sumStats.getTradesProfitPercentage()),
                format(sumStats.getDividendSum())};
    }
}
