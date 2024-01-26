package org.kaleta.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TradeSellDto
{
    private String date;
    private String price;
    private String fees;
    private List<Trade> trades = new ArrayList<>();

    @Data
    public static class Trade
    {
        private String tradeId;
        private String quantity;

        public Trade() {}
        public Trade(String tradeId, String quantity)
        {
            this.tradeId = tradeId;
            this.quantity = quantity;
        }
    }
}
