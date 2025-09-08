package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.joda.time.DateTime;
import org.kaleta.Utils;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Dividend;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.model.StatsByCompany;
import org.kaleta.model.StatsByPeriod;

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

    public List<StatsByCompany> getByCompany(String year, String sector)
    {
        Map<String, StatsByCompany> companyMap = new HashMap<>();
        List<Trade> trades = tradeService.getTrades(false, null, null, null, year, sector);
        for (Trade trade : trades)
        {
            if (!companyMap.containsKey(trade.getTicker())) {
                companyMap.put(trade.getTicker(), new StatsByCompany(trade.getTicker(), trade.getCurrency()));
            }
            companyMap.get(trade.getTicker()).addPurchase(trade.getPurchaseTotal());
            companyMap.get(trade.getTicker()).addSell(trade.getSellTotal());
            companyMap.get(trade.getTicker()).addYear(format(trade.getSellDate()).split("\\.")[2]);
        }
        List<Dividend> dividends = dividendService.getDividends(null , null, year, sector);
        for (Dividend dividend : dividends)
        {
            if (!companyMap.containsKey(dividend.getTicker())) {
                companyMap.put(dividend.getTicker(), new StatsByCompany(dividend.getTicker(), dividend.getCurrency()));
            }
            companyMap.get(dividend.getTicker()).addDividend(dividend.getTotal());
            companyMap.get(dividend.getTicker()).addYear(format(dividend.getDate()).split("\\.")[2]);
        }
        return new ArrayList<>(companyMap.values());
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

    public List<StatsByPeriod> getByPeriod(String companyId, boolean isMonthly, String sector)
    {
        Map<String, StatsByPeriod> map = new HashMap<>();
        List<Trade> trades = tradeService.getTrades(false, companyId, null, null, null, sector);
        for (Trade trade : trades)
        {
            String period = Utils.format(trade.getSellDate()).substring(isMonthly ? 3 : 6);
            if (!map.containsKey(period)) {
                map.put(period, new StatsByPeriod(period));
            }
            map.get(period).addPurchase(trade.getPurchaseTotal().multiply(trade.getCurrency().toUsd()).setScale(2, RoundingMode.HALF_UP));
            map.get(period).addSell(trade.getSellTotal().multiply(trade.getCurrency().toUsd()).setScale(2, RoundingMode.HALF_UP));
            map.get(period).increaseTrade();
        }
        List<Dividend> dividends = dividendService.getDividends(companyId , null, null, sector);
        for (Dividend dividend : dividends)
        {
            String period = Utils.format(dividend.getDate()).substring(isMonthly ? 3 : 6);
            if (!map.containsKey(period)) {
                map.put(period, new StatsByPeriod(period));
            }
            map.get(period).addDividend(dividend.getTotal().multiply(dividend.getCurrency().toUsd()).setScale(2, RoundingMode.HALF_UP));
        }
        if (isMonthly){
            Set<String> years = map.values().stream().map(stat -> stat.getPeriod().substring(3)).collect(Collectors.toSet());
            for (String year : years) {
                for (int i=1; i<=12; i++) {
                    String month = String.format("%02d", i) + "." + year;
                    if (!map.containsKey(month) && (Integer.parseInt(year) < DateTime.now().getYear() || i <= DateTime.now().getMonthOfYear())){
                        map.put(month, new StatsByPeriod(month));
                    }
                }
            }
        }
        List<StatsByPeriod> monthlyStats = new ArrayList<>(map.values());
        monthlyStats.sort(isMonthly ? StatsByPeriod::compareMonthTo : StatsByPeriod::compareYearTo);
        return monthlyStats;
    }

    public String[] computePeriodSums(List<StatsByPeriod> periodStats)
    {
        StatsByPeriod sumStats = new StatsByPeriod();
        int tradesSum = 0;
        for (StatsByPeriod stats : periodStats)
        {
            tradesSum += stats.getTradesCount();
            sumStats.addPurchase(stats.getPurchaseSum());
            sumStats.addSell(stats.getSellSum());
            sumStats.addDividend(stats.getDividendSum());
        }
        return new String[]{String.valueOf(periodStats.size()),
                String.valueOf(tradesSum), format(sumStats.getTradesProfit()), format(sumStats.getTradesProfitPercentage()),
                format(sumStats.getDividendSum())};
    }
}
