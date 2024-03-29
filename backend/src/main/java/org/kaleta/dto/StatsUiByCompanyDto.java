package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.entity.Currency;
import org.kaleta.model.StatsByCompany;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class StatsUiByCompanyDto
{
    private List<String> columns = new ArrayList<>();
    private List<StatRow> rows = new ArrayList<>();

    private String[] sums = new String[8];
    private Set<String> years = new HashSet<>();

    @Data
    public static class StatRow
    {
        private String ticker;
        private Currency currency;
        private String purchaseSum;
        private String sellSum;
        private String dividendSum;
        private String profit;
        private String profitUsd;
        private String profitPercentage;
    }

    public StatsUiByCompanyDto()
    {
        columns.add("Ticker");
        columns.add("#");
        columns.add("Purchases");
        columns.add("Sells");
        columns.add("Dividends");
        columns.add("Profit");
        columns.add("Profit $");
        columns.add("Profit %");
    }

    public static StatsUiByCompanyDto from(List<StatsByCompany> companyStatsList)
    {
        StatsUiByCompanyDto dto = new StatsUiByCompanyDto();
        for (StatsByCompany companyStats : companyStatsList)
        {
            StatRow row = new StatRow();
            row.setTicker(companyStats.getTicker());
            row.setCurrency(companyStats.getCurrency());
            row.setPurchaseSum(Utils.format(companyStats.getPurchaseSum()));
            row.setSellSum(Utils.format(companyStats.getSellSum()));
            row.setDividendSum(Utils.format(companyStats.getDividendSum()));
            row.setProfit(Utils.format(companyStats.getProfit()));
            row.setProfitUsd(Utils.format(companyStats.getProfitUsd()));
            row.setProfitPercentage(Utils.format(companyStats.getProfitPercentage()));
            dto.getRows().add(row);
            dto.getYears().addAll(companyStats.getYears());
        }
        return dto;
    }
}
