package org.kaleta.model;

import lombok.Data;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Data
public class Trades
{
    private List<Trade> trades = new ArrayList<>();
    private Aggregates aggregates;

    @Data
    public static class Trade implements Comparable<Trade>{
        private Long id;
        private Company company;
        private Portfolio portfolio;

        private Date purchaseDate;
        private BigDecimal purchaseQuantity;
        private BigDecimal purchasePrice;
        private BigDecimal purchaseFees;
        private BigDecimal purchaseTotal;

        private Date sellDate;
        private BigDecimal sellQuantity;
        private BigDecimal sellPrice;
        private BigDecimal sellFees;
        private BigDecimal sellTotal;

        private BigDecimal profit;
        private BigDecimal profitPercentage;

        @Override
        public int compareTo(Trade other)
        {
            int purchaseDateCompare = -this.getPurchaseDate().compareTo(other.getPurchaseDate());
            if (purchaseDateCompare != 0) {
                return purchaseDateCompare;
            } else {
                if (this.getSellDate() == null && other.getSellDate() == null) return 0;
                if (this.getSellDate() == null && other.getSellDate() != null) return -1;
                if (this.getSellDate() != null && other.getSellDate() == null) return 1;
                return  -this.getSellDate().compareTo(other.getSellDate());
            }
        }
    }

    @Data
    public static class Portfolio implements Comparable<Portfolio>
    {
        private String key;
        private String name;
        private String abbreviation;

        public Portfolio() {}

        public Portfolio(org.kaleta.persistence.entity.Portfolio portfolio)
        {
            this.key = portfolio.toString();
            this.name = portfolio.getName();
            this.abbreviation = portfolio.getAbbreviation();
        }

        @Override
        public int compareTo(Portfolio other)
        {
            if (other == null) return -1;
            return this.getKey().compareTo(other.getKey());
        }
    }

    @Data
    public static class Aggregates
    {
        private Integer companies;
        private Integer currencies;
        private BigDecimal purchaseFees;
        private BigDecimal purchaseTotal;
        private BigDecimal sellFees;
        private BigDecimal sellTotal;
        private BigDecimal profit;
        private BigDecimal profitPercentage;
    }
}
