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
        private Long id;
        private PeriodName name;
        private YearMonth endingMonth;
        private Date reportDate;
        private Date previousReportDate;
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

        private final Metric revenue = new Metric();
        private final Metric grossProfit = new Metric();
        private final Metric operatingIncome = new Metric();
        private final Metric netIncome = new Metric();

        private BigDecimal dividend;
        private BigDecimal shares;

        @Data
        public static class Metric {
            private BigDecimal value;
            private BigDecimal margin;
            private BigDecimal yoy;
            private BigDecimal qoq;
        }
    }
}
