package org.kaleta.model;

import lombok.Data;
import org.kaleta.persistence.entity.PeriodName;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Data
public class Periods
{
    private List<Period> periods = new ArrayList<>();
    private List<Financial> financials = new ArrayList<>();
    private Financial ttm;

    @Data
    public static class Period
    {
        private String id;
        private PeriodName name;
        private YearMonth endingMonth;
        private Date reportDate;
        private BigDecimal shares;
        private BigDecimal priceLow;
        private BigDecimal priceHigh;
        private String research;
        private Financial financial;
    }

    @Data
    public static class Financial
    {
        private PeriodName period;
        private BigDecimal revenue;
        private BigDecimal costGoodsSold;
        private BigDecimal grossProfit;
        private BigDecimal grossMargin;
        private BigDecimal operatingExpenses;
        private BigDecimal operatingIncome;
        private BigDecimal operatingMargin;
        private BigDecimal netIncome;
        private BigDecimal netMargin;
        private BigDecimal dividend;
    }
}
