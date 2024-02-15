package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.model.StatsByPeriod;

import java.util.ArrayList;
import java.util.List;

@Data
public class StatsUiByPeriodDto
{
    private List<Column> columns = new ArrayList<>();
    private List<StatRow> rows = new ArrayList<>();

    private String[] sums = new String[5];

    @Data
    public static class StatRow
    {
        private String period;
        private String tradesCount;
        private String tradesProfit;
        private String tradesProfitPercentage;
        private String dividendSum;
    }

    @Data
    public static class Column
    {
        private String name;
        private List<String> subColumns;

        public Column(String name, List<String> subColumns) {this.name = name;this.subColumns = subColumns;}
    }

    public StatsUiByPeriodDto(){}
    public StatsUiByPeriodDto(boolean isMonthly)
    {
        columns.add(new Column(isMonthly ? "Month" : "Year", new ArrayList<>()));
        columns.add(new Column("Trades", List.of("Count", "Profit $", "Profit %")));
        columns.add(new Column("Dividends $", new ArrayList<>()));
    }

    public static StatsUiByPeriodDto from(List<StatsByPeriod> monthlyStatsList, boolean isMonthly)
    {
        StatsUiByPeriodDto dto = new StatsUiByPeriodDto(isMonthly);
        for (StatsByPeriod monthlyStats : monthlyStatsList)
        {
            StatsUiByPeriodDto.StatRow row = new StatsUiByPeriodDto.StatRow();
            row.setPeriod(monthlyStats.getPeriod());
            row.setTradesCount(String.valueOf(monthlyStats.getTradesCount()));
            row.setTradesProfit(Utils.format(monthlyStats.getTradesProfit()));
            row.setTradesProfitPercentage(Utils.format(monthlyStats.getTradesProfitPercentage()));
            row.setDividendSum(Utils.format(monthlyStats.getDividendSum()));
            dto.getRows().add(row);
        }
        return dto;
    }
}
