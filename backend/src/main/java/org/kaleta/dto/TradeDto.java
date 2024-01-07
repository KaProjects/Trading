package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Constants;
import org.kaleta.Utils;
import org.kaleta.entity.Currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class TradeDto
{
    private List<Column> columns = new ArrayList<>();
    private List<Trade> trades = new ArrayList<>();

    public TradeDto()
    {
        columns.add(new Column("Ticker", new ArrayList<>()));
        columns.add(new Column("#", new ArrayList<>()));
        columns.add(new Column("Purchase", List.of("Date", "Quantity", "Price", "Fees", "Total")));
        columns.add(new Column("Sale", List.of("Date", "Quantity", "Price", "Fees", "Total")));
        columns.add(new Column("Profit", new ArrayList<>()));
        columns.add(new Column("Profit %", new ArrayList<>()));
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
        private BigDecimal purchaseQuantity;
        private BigDecimal purchasePrice;
        private BigDecimal purchaseFees;
        private BigDecimal purchaseTotal;

        private String sellDate;
        private BigDecimal sellQuantity;
        private BigDecimal sellPrice;
        private BigDecimal sellFees;
        private BigDecimal sellTotal;

        private BigDecimal profit;
        private BigDecimal profitPercentage;

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

            dto.setPurchaseDate(Constants.dateFormat.format(trade.getPurchaseDate()));
            dto.setPurchaseQuantity(trade.getQuantity());
            dto.setPurchasePrice(trade.getPurchasePrice());
            dto.setPurchaseFees(trade.getPurchaseFees());
            dto.setPurchaseTotal(trade.getPurchaseTotal());

            if (trade.getSellDate() != null)
            {
                dto.setSellDate(Constants.dateFormat.format(trade.getSellDate()));
                dto.setSellQuantity(trade.getQuantity());
                dto.setSellPrice(trade.getSellPrice());
                dto.setSellFees(trade.getSellFees());
                dto.setSellTotal(trade.getSellTotal());

                dto.setProfit(trade.getProfit());
                dto.setProfitPercentage(trade.getProfitPercentage());
            }

            tradeDto.getTrades().add(dto);
        }
        tradeDto.getTrades().sort(Trade::compareTo);
        return tradeDto;
    }
}
