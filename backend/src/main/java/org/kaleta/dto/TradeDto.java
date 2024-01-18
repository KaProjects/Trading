package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Constants;
import org.kaleta.Utils;
import org.kaleta.entity.Currency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.kaleta.Utils.format;

@Data
public class TradeDto
{
    private List<Column> columns = new ArrayList<>();
    private List<Trade> trades = new ArrayList<>();
    private String[] sums = new String[14];

    public TradeDto()
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

    @Data
    public static class Trade implements Comparable<Trade>
    {
        private String ticker;
        private Currency currency;

        private String purchaseDate;
        private String purchaseQuantity;
        private String purchasePrice;
        private String purchaseFees;
        private String purchaseTotal;

        private String sellDate;
        private String sellQuantity;
        private String sellPrice;
        private String sellFees;
        private String sellTotal;

        private String profit;
        private String profitPercentage;

        @Override
        public int compareTo(Trade other)
        {
            return -Utils.compareDates(this.getPurchaseDate(), other.getPurchaseDate());
        }
    }

    public static TradeDto from(List<org.kaleta.entity.Trade> trades)
    {
        TradeDto tradeDto = new TradeDto();

        for (org.kaleta.entity.Trade trade : trades) {
            TradeDto.Trade dto = new TradeDto.Trade();
            dto.setTicker(trade.getTicker());
            dto.setCurrency(trade.getCurrency());

            dto.setPurchaseDate(Constants.dateFormatDto.format(trade.getPurchaseDate()));
            dto.setPurchaseQuantity(format(trade.getQuantity()));
            dto.setPurchasePrice(format(trade.getPurchasePrice()));
            dto.setPurchaseFees(format(trade.getPurchaseFees()));
            dto.setPurchaseTotal(format(trade.getPurchaseTotal()));

            if (trade.getSellDate() != null)
            {
                dto.setSellDate(Constants.dateFormatDto.format(trade.getSellDate()));
                dto.setSellQuantity(format(trade.getQuantity()));
                dto.setSellPrice(format(trade.getSellPrice()));
                dto.setSellFees(format(trade.getSellFees()));
                dto.setSellTotal(format(trade.getSellTotal()));

                dto.setProfit(format(trade.getProfit()));
                dto.setProfitPercentage(format(trade.getProfitPercentage()));
            }

            tradeDto.getTrades().add(dto);
        }
        tradeDto.getTrades().sort(Trade::compareTo);
        return tradeDto;
    }
}
