package org.kaleta.rest.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.kaleta.persistence.entity.Sort;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompanyUiDto
{
    private List<String> columns = new ArrayList<>();
    private List<Company> companies = new ArrayList<>();
    private List<Sort.CompanyAggregate> sorts = new ArrayList<>();

    public CompanyUiDto()
    {
        columns.add("Ticker");
        columns.add("#");
        columns.add("*");
        columns.add("Sector");
        columns.add("Total Trades");
        columns.add("Active Trades");
        columns.add("Dividends");
        columns.add("Records");
        columns.add("Financials");

        sorts.addAll(List.of(Sort.CompanyAggregate.values()));
    }

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Company extends org.kaleta.model.Company
    {
        private int totalTrades = 0;
        private int activeTrades = 0;
        private int dividends = 0;
        private int records = 0;
        private int financials = 0;

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
}
