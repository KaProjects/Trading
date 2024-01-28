package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Constants;
import org.kaleta.Utils;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Trade;

import static org.kaleta.Utils.format;

@Data
public class TradeDto implements Comparable<TradeDto>
{
    private String id;
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
    public int compareTo(TradeDto other)
    {
        return -Utils.compareDtoDates(this.getPurchaseDate(), other.getPurchaseDate());
    }

    public static TradeDto from(Trade trade)
    {
        TradeDto dto = new TradeDto();
        dto.setId(trade.getId());
        dto.setTicker(trade.getTicker());
        dto.setCurrency(trade.getCurrency());

        dto.setPurchaseDate(Utils.format(trade.getPurchaseDate()));
        dto.setPurchaseQuantity(format(trade.getQuantity()));
        dto.setPurchasePrice(format(trade.getPurchasePrice()));
        dto.setPurchaseFees(format(trade.getPurchaseFees()));
        dto.setPurchaseTotal(format(trade.getPurchaseTotal()));

        if (trade.getSellDate() != null)
        {
            dto.setSellDate(Utils.format(trade.getSellDate()));
            dto.setSellQuantity(format(trade.getQuantity()));
            dto.setSellPrice(format(trade.getSellPrice()));
            dto.setSellFees(format(trade.getSellFees()));
            dto.setSellTotal(format(trade.getSellTotal()));

            dto.setProfit(format(trade.getProfit()));
            dto.setProfitPercentage(format(trade.getProfitPercentage()));
        }
        return dto;
    }
}
