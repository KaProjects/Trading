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

    public void sort(Integer index) {
        if (Integer.valueOf(7).equals(index)) {
            companies.sort(Comparator.comparing(Company::getProfitPercentage, Comparator.nullsLast(Comparator.reverseOrder())));
        } else {
            companies.sort(Comparator.comparing(Company::getProfitSum).reversed());
        }
    }
}
