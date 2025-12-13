package org.kaleta.rest.dto;

import lombok.Data;
import org.kaleta.dto.TradeDto;

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
}
