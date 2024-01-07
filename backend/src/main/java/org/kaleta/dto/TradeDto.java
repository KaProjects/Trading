package org.kaleta.dto;

import lombok.Data;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Trade;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Data
public class TradeDto
{
    private String ticker;
    private Currency currency;
    private BigDecimal quantity;
    private Date purchaseDate;
    private BigDecimal purchasePrice;
    private BigDecimal purchaseFees;
    private Date sellDate;
    private BigDecimal sellPrice;
    private BigDecimal sellFees;

    public static List<TradeDto> from(List<Trade> trades)
    {
        List<TradeDto> list = new ArrayList<>();
        for (Trade trade : trades) {
            list.add(from(trade));
        }
        return list;
    }

    public static TradeDto from(Trade trade)
    {
        TradeDto dto = new TradeDto();
        dto.setTicker(trade.getTicker().trim());
        dto.setCurrency(trade.getCurrency());
        dto.setQuantity(trade.getQuantity());
        dto.setPurchaseDate(trade.getPurchaseDate());
        dto.setPurchasePrice(trade.getPurchasePrice());
        dto.setPurchaseFees(trade.getPurchaseFees());
        dto.setSellDate(trade.getSellDate());
        dto.setSellPrice(trade.getSellPrice());
        dto.setSellFees(trade.getSellFees());
        return dto;
    }
}
