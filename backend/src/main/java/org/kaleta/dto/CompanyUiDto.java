package org.kaleta.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.kaleta.entity.Sort;

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
        columns.add("Shares");
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
    public static class Company extends CompanyDto
    {
        private int totalTrades = 0;
        private int activeTrades = 0;
        private int dividends = 0;
        private int records = 0;
        private int financials = 0;

        public static Company from(org.kaleta.entity.Company company)
        {
            Company dto = new Company();
            dto.setId(company.getId());
            dto.setTicker(company.getTicker());
            dto.setCurrency(company.getCurrency());
            dto.setWatching(company.isWatching());
            dto.setSector(SectorDto.from(company.getSector()));
            dto.setShares(company.getShares());
            return dto;
        }
    }
}
