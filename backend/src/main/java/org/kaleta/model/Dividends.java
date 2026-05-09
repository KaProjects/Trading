package org.kaleta.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.kaleta.persistence.entity.Currency;

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
    @JsonIgnoreProperties(value = {"ticker", "currency"}, allowGetters = true)
    public static class Dividend implements Comparable<Dividend>
    {
        private String id;
        private Company company;

        private Date date;
        private BigDecimal dividend;
        private BigDecimal tax;
        private BigDecimal net;

        public String getTicker() {
            return company.getTicker();
        }

        public Currency getCurrency()
        {
            return company.getCurrency();
        }

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
