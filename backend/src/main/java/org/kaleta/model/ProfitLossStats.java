package org.kaleta.model;

import lombok.Data;
import org.kaleta.persistence.entity.Currency;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProfitLossStats
{
    private Currency currency;
    private int tradesCount;
    private int dividendsCount;
    private boolean dividendsExcluded;
    private List<Point> points = new ArrayList<>();

    public enum Type
    {
        TRADE,
        DIVIDEND
    }

    @Data
    public static class Point
    {
        private Integer eventNumber;
        private Type type;
        private Long sourceId;
        private Date date;
        private String ticker;
        private BigDecimal amount;
        private BigDecimal cumulativeProfit;
    }
}
