package org.kaleta.model;

import lombok.Data;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Data
public class Dividends
{
    private List<Dividend> dividends = new ArrayList<>();
    private Aggregates aggregates;

    @Data
    public static class Dividend implements Comparable<Dividend>
    {
        private String id;
        private Company company;

        private Date date;
        private BigDecimal dividend;
        private BigDecimal tax;
        private BigDecimal net;

        @Override
        public int compareTo(Dividend other)
        {
            return -this.getDate().compareTo(other.getDate());
        }
    }

    @Data
    public static class Aggregates
    {
        private Integer companies;
        private Integer currencies;
        private BigDecimal dividendSum;
        private BigDecimal taxSum;
        private BigDecimal netSum;
    }
}
