package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.model.StatsByMonth;

import java.util.ArrayList;
import java.util.List;

@Data
public class StatsUiByMonthDto
{
    private List<Column> columns = new ArrayList<>();
    private List<StatRow> rows = new ArrayList<>();

    private String[] sums = new String[5];

    @Data
    public static class StatRow
    {
        private String month;
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

    public StatsUiByMonthDto()
    {
        columns.add(new Column("Month", new ArrayList<>()));
        columns.add(new Column("Trades", List.of("Count", "Profit", "Profit %")));
        columns.add(new Column("Dividends", new ArrayList<>()));
    }

    public static StatsUiByMonthDto from(List<StatsByMonth> monthlyStatsList)
    {
        StatsUiByMonthDto dto = new StatsUiByMonthDto();
        for (StatsByMonth monthlyStats : monthlyStatsList)
        {
            StatsUiByMonthDto.StatRow row = new StatsUiByMonthDto.StatRow();
            row.setMonth(monthlyStats.getMonth());
            row.setTradesCount(String.valueOf(monthlyStats.getTradesCount()));
            row.setTradesProfit(Utils.format(monthlyStats.getTradesProfit()));
            row.setTradesProfitPercentage(Utils.format(monthlyStats.getTradesProfitPercentage()));
            row.setDividendSum(Utils.format(monthlyStats.getDividendSum()));
            dto.getRows().add(row);
        }
        return dto;
    }
}
