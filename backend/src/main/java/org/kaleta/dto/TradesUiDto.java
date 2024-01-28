package org.kaleta.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class TradesUiDto
{
    private List<Column> columns = new ArrayList<>();
    private List<TradeDto> trades = new ArrayList<>();
    private String[] sums = new String[14];

    public TradesUiDto()
    {
        columns.add(new Column("Ticker", new ArrayList<>()));
        columns.add(new Column("#", new ArrayList<>()));
        columns.add(new Column("Purchase", List.of("Date", "Quantity", "Price", "Fees", "Total")));
        columns.add(new Column("Sale", List.of("Date", "Quantity", "Price", "Fees", "Total")));
        columns.add(new Column("Profit", new ArrayList<>()));
        columns.add(new Column("Profit %", new ArrayList<>()));
        Arrays.fill(sums, "");
    }

    @Data
    public static class Column
    {
        private String name;
        private List<String> subColumns;

        public Column(String name, List<String> subColumns) {this.name = name;this.subColumns = subColumns;}
    }

    public static TradesUiDto from(List<org.kaleta.entity.Trade> trades)
    {
        TradesUiDto tradesUiDto = new TradesUiDto();

        for (org.kaleta.entity.Trade trade : trades) {
            tradesUiDto.getTrades().add(TradeDto.from(trade));
        }
        tradesUiDto.getTrades().sort(TradeDto::compareTo);
        return tradesUiDto;
    }
}
