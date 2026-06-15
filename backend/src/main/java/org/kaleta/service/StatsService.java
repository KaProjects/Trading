package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.model.Company;
import org.kaleta.model.CompanyStats;
import org.kaleta.model.Dividends;
import org.kaleta.model.PeriodFrequency;
import org.kaleta.model.PeriodStats;
import org.kaleta.model.Trades;
import org.kaleta.persistence.entity.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class StatsService
{
    @Inject
    TradeService tradeService;
    @Inject
    DividendService dividendService;
    @Inject
    ArithmeticService arithmeticService;

    public CompanyStats getByCompany(String year, String sector)
    {
        Map<Company, List<Trades.Trade>> tradesByCompany = tradeService.getByCompany(null, null, year, sector);
        Map<Company, List<Dividends.Dividend>> dividendsByCompany = dividendService.getByCompany(null, year, sector);

        CompanyStats model = new CompanyStats();

        List<Company> companies = Stream
                .concat(tradesByCompany.keySet().stream(), dividendsByCompany.keySet().stream())
                .distinct()
                .collect(Collectors.toList());

        for (Company company : companies){
            CompanyStats.Company companyStats = new CompanyStats.Company();
            companyStats.setTicker(company.getTicker());
            companyStats.setCurrency(company.getCurrency());

            BigDecimal purchaseSum = BigDecimal.ZERO;
            BigDecimal sellSum = BigDecimal.ZERO;

            for (Trades.Trade trade : tradesByCompany.getOrDefault(company, List.of())){
                purchaseSum = purchaseSum.add(trade.getPurchaseTotal());
                sellSum = sellSum.add(trade.getSellTotal());
                model.getYears().add(trade.getSellDate().toString().split("-")[0]);
            }

            BigDecimal dividendSum = BigDecimal.ZERO;

            for (Dividends.Dividend dividend : dividendsByCompany.getOrDefault(company, List.of())) {
                dividendSum = dividendSum.add(dividend.getNet());
                model.getYears().add(dividend.getDate().toString().split("-")[0]);
            }

            companyStats.setPurchaseSum(purchaseSum);
            companyStats.setSellSum(sellSum);
            companyStats.setDividendSum(dividendSum);
            companyStats.setProfitSum(sellSum.subtract(purchaseSum).add(dividendSum));
            companyStats.setProfitUsdSum(
                    companyStats.getProfitSum()
                            .multiply(companyStats.getCurrency().toUsd())
                            .setScale(2, RoundingMode.HALF_UP));

            if (!arithmeticService.equalsBigDecimal(purchaseSum, BigDecimal.ZERO)) {
                companyStats.setProfitPercentage(
                        sellSum.add(dividendSum)
                                .divide(purchaseSum, 4, RoundingMode.HALF_UP)
                                .subtract(new BigDecimal(1))
                                .multiply(new BigDecimal(100)));
            }

            model.getCompanies().add(companyStats);
        }

        model.setAggregates(computeCompanyAggregates(model.getCompanies()));

        return model;
    }

    public PeriodStats getByPeriod(PeriodFrequency frequency, String companyId, String sector)
    {
        Map<String, List<Trades.Trade>> tradesByPeriod = tradeService.getByPeriod(frequency, companyId, null, sector);
        Map<String, List<Dividends.Dividend>> dividendsByPeriod = dividendService.getByPeriod(frequency, companyId , null, sector);

        PeriodStats model = new PeriodStats();

        List<String> periods = Stream
                .concat(tradesByPeriod.keySet().stream(), dividendsByPeriod.keySet().stream())
                .distinct()
                .collect(Collectors.toList());

        for (String period : periods){
            PeriodStats.Period periodStats = new PeriodStats.Period();
            periodStats.setPeriod(period);

            periodStats.setTradesCount(tradesByPeriod.getOrDefault(period, List.of()).size());

            BigDecimal purchaseSum = BigDecimal.ZERO;
            BigDecimal sellSum = BigDecimal.ZERO;

            for (Trades.Trade trade : tradesByPeriod.getOrDefault(period, List.of())){
                purchaseSum = purchaseSum.add(trade.getPurchaseTotal());
                sellSum = sellSum.add(trade.getSellTotal());
            }

            periodStats.setTradesPurchaseSum(purchaseSum);
            periodStats.setTradesSellSum(sellSum);
            periodStats.setTradesProfitSum(sellSum.subtract(purchaseSum));

            if (!arithmeticService.equalsBigDecimal(purchaseSum, BigDecimal.ZERO)) {
                periodStats.setTradesProfitPercentage(
                        sellSum.divide(purchaseSum, 4, RoundingMode.HALF_UP)
                                .subtract(new BigDecimal(1))
                                .multiply(new BigDecimal(100)));
            }

            BigDecimal dividendSum = BigDecimal.ZERO;

            for (Dividends.Dividend dividend : dividendsByPeriod.getOrDefault(period, List.of())) {
                dividendSum = dividendSum.add(dividend.getNet());
            }

            periodStats.setDividendSum(dividendSum);

            model.getPeriods().add(periodStats);
        }

        model.getPeriods().sort(Comparator.comparing(PeriodStats.Period::getPeriod).reversed());

        model.setAggregates(computePeriodAggregates(model.getPeriods()));

        return model;
    }

    private PeriodStats.Aggregates computePeriodAggregates(List<PeriodStats.Period> periodStats)
    {
        Integer tradesSum = 0;
        BigDecimal purchaseSum = BigDecimal.ZERO;
        BigDecimal sellSum = BigDecimal.ZERO;
        BigDecimal dividendSum = BigDecimal.ZERO;
        BigDecimal profitSum = BigDecimal.ZERO;

        for (PeriodStats.Period stats : periodStats)
        {
            tradesSum += stats.getTradesCount();
            purchaseSum = purchaseSum.add(stats.getTradesPurchaseSum());
            sellSum = sellSum.add(stats.getTradesSellSum());
            dividendSum = dividendSum.add(stats.getDividendSum());
            profitSum = profitSum.add(stats.getTradesProfitSum());
        }

        PeriodStats.Aggregates  aggregates = new PeriodStats.Aggregates();
        aggregates.setPeriods(periodStats.size());
        aggregates.setTradesCount(tradesSum);
        aggregates.setTradesProfitSum(profitSum);
        aggregates.setDividendSum(dividendSum);

        if (!arithmeticService.equalsBigDecimal(purchaseSum, BigDecimal.ZERO)){
            aggregates.setTradesProfitPercentage(
                    sellSum.divide(purchaseSum, 4, RoundingMode.HALF_UP)
                            .subtract(new BigDecimal(1))
                            .multiply(new BigDecimal(100)));
        }

        return aggregates;
    }

    private CompanyStats.Aggregates computeCompanyAggregates(List<CompanyStats.Company> companyStats)
    {
        Set<String> companies = new HashSet<>();
        Set<Currency> currencies = new HashSet<>();
        BigDecimal purchaseSum = BigDecimal.ZERO;
        BigDecimal sellSum = BigDecimal.ZERO;
        BigDecimal dividendSum = BigDecimal.ZERO;
        BigDecimal profitSum = BigDecimal.ZERO;
        BigDecimal profitUsdSum = BigDecimal.ZERO;

        for (CompanyStats.Company stats : companyStats)
        {
            companies.add(stats.getTicker());
            currencies.add(stats.getCurrency());
            purchaseSum = purchaseSum.add(stats.getPurchaseSum());
            sellSum = sellSum.add(stats.getSellSum());
            dividendSum = dividendSum.add(stats.getDividendSum());
            profitSum = profitSum.add(stats.getProfitSum());
            profitUsdSum = profitUsdSum.add(stats.getProfitUsdSum());
        }
        CompanyStats.Aggregates aggregates = new CompanyStats.Aggregates();
        aggregates.setCompanies(companies.size());
        aggregates.setCurrencies(currencies.size());
        aggregates.setPurchaseSum(purchaseSum);
        aggregates.setSellSum(sellSum);
        aggregates.setDividendSum(dividendSum);
        aggregates.setProfitSum(profitSum);
        aggregates.setProfitSumUsd(profitUsdSum);

        if (!arithmeticService.equalsBigDecimal(purchaseSum, BigDecimal.ZERO)){
            aggregates.setProfitPercentage(
                    sellSum.add(dividendSum)
                            .divide(purchaseSum, 4, RoundingMode.HALF_UP)
                            .subtract(new BigDecimal(1))
                            .multiply(new BigDecimal(100)));
        }

        return aggregates;
    }
}
