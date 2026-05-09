package org.kaleta.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PeriodStats
{
    private List<Period> periods = new ArrayList<>();
    private Aggregates aggregates;

    @Data
    public static class Period
    {
        private String period;
        private Integer tradesCount;
        private BigDecimal tradesPurchaseSum;
        private BigDecimal tradesSellSum;
        private BigDecimal tradesProfitSum;
        private BigDecimal tradesProfitPercentage;
        private BigDecimal dividendSum;
    }

    @Data
    public static class Aggregates
    {
        private Integer periods;
        private Integer tradesCount;
        private BigDecimal tradesProfitSum;
        private BigDecimal tradesProfitPercentage;
        private BigDecimal dividendSum;
    }
}
