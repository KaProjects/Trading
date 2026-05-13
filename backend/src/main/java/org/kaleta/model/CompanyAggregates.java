package org.kaleta.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
public class CompanyAggregates
{
    private List<Company> companies = new ArrayList<>();
    private List<Sort> sorts = List.of(Sort.values());

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Company extends org.kaleta.model.Company
    {
        private Integer totalTrades = 0;
        private Integer activeTrades = 0;
        private Integer dividends = 0;
        private Integer records = 0;
        private Integer periods = 0;

        public Company(){}
        public Company(org.kaleta.model.Company company)
        {
            this.setId(company.getId());
            this.setTicker(company.getTicker());
            this.setCurrency(company.getCurrency());
            this.setWatching(company.getWatching());
            this.setSector(company.getSector());
        }
    }

    public enum Sort {
        TICKER, CURRENCY, WATCHING, SECTOR, ALL_TRADES, ACTIVE_TRADES, DIVIDENDS, RECORDS, PERIODS
    }

    public void sort(Sort sort) {
        switch (sort) {
            case TICKER:
                companies.sort(Comparator.comparing(Company::getTicker));
                break;
            case CURRENCY:
                companies.sort(Comparator.comparing(Company::getCurrency));
                break;
            case WATCHING:
                companies.sort(Comparator.comparing(Company::getWatching, Comparator.reverseOrder()));
                break;
            case SECTOR:
                companies.sort(Comparator.comparing(Company::getSector, Comparator.nullsLast(Comparator.comparing(Company.Sector::getKey))));
                break;
            case ALL_TRADES:
                companies.sort(Comparator.comparing(Company::getTotalTrades, Comparator.reverseOrder()));
                break;
            case ACTIVE_TRADES:
                companies.sort(Comparator.comparing(Company::getActiveTrades, Comparator.reverseOrder()));
                break;
            case DIVIDENDS:
                companies.sort(Comparator.comparing(Company::getDividends, Comparator.reverseOrder()));
                break;
            case RECORDS:
                companies.sort(Comparator.comparing(Company::getRecords, Comparator.reverseOrder()));
                break;
            case PERIODS:
                companies.sort(Comparator.comparing(Company::getPeriods, Comparator.reverseOrder()));
                break;
        }
    }
}
