package org.kaleta.model;

import lombok.Data;
import org.kaleta.persistence.entity.Currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class CompanyStats
{
    private List<Company> companies = new ArrayList<>();
    private Aggregates aggregates;
    private Set<String> years = new HashSet<>();
    private List<Sort> sorts = List.of(Sort.values());

    @Data
    public static class Company
    {
        private String ticker;
        private Currency currency;
        private BigDecimal purchaseSum;
        private BigDecimal sellSum;
        private BigDecimal dividendSum;
        private BigDecimal profitSum;
        private BigDecimal profitUsdSum;
        private BigDecimal profitPercentage;
    }

    @Data
    public static class Aggregates
    {
        private Integer companies;
        private Integer currencies;
        private BigDecimal purchaseSum;
        private BigDecimal sellSum;
        private BigDecimal dividendSum;
        private BigDecimal profitSum;
        private BigDecimal profitSumUsd;
        private BigDecimal profitPercentage;
    }

    public enum Sort {
        TICKER,	CURRENCY, PURCHASES, SELLS, DIVIDENDS, PROFIT, PROFIT_USD, PROFIT_PERCENT
    }

    public void sort(Sort sort) {
        switch (sort) {
            case TICKER:
                companies.sort(Comparator.comparing(Company::getTicker));
                break;
            case CURRENCY:
                companies.sort(Comparator.comparing(Company::getCurrency));
                break;
            case PURCHASES:
                companies.sort(Comparator.comparing(Company::getPurchaseSum, Comparator.nullsLast(Comparator.reverseOrder())));
                break;
            case SELLS:
                companies.sort(Comparator.comparing(Company::getSellSum, Comparator.nullsLast(Comparator.reverseOrder())));
                break;
            case DIVIDENDS:
                companies.sort(Comparator.comparing(Company::getDividendSum, Comparator.nullsLast(Comparator.reverseOrder())));
                break;
            case PROFIT:
                companies.sort(Comparator.comparing(Company::getProfitSum, Comparator.nullsLast(Comparator.reverseOrder())));
                break;
            case PROFIT_USD:
                companies.sort(Comparator.comparing(Company::getProfitUsdSum, Comparator.nullsLast(Comparator.reverseOrder())));
                break;
            case PROFIT_PERCENT:
                companies.sort(Comparator.comparing(Company::getProfitPercentage, Comparator.nullsLast(Comparator.reverseOrder())));
                break;
        }
    }
}
