package org.kaleta.model;

import lombok.Data;
import org.kaleta.entity.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class StatsByCompany
{
    private String ticker;
    private Currency currency;

    private BigDecimal purchaseSum = new BigDecimal(0);
    private BigDecimal sellSum = new BigDecimal(0);
    private BigDecimal dividendSum = new BigDecimal(0);

    public StatsByCompany() {}
    public StatsByCompany(String ticker, Currency currency) {this.ticker = ticker;this.currency = currency;}

    public int compareProfitTo(StatsByCompany other)
    {
        return -this.getProfitUsd().compareTo(other.getProfitUsd());
    }

    public BigDecimal getProfit()
    {
        return sellSum.subtract(purchaseSum).add(dividendSum);
    }

    public BigDecimal getProfitUsd()
    {
        return getProfit().multiply(currency.toUsd()).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getProfitPercentage()
    {
        if (purchaseSum.compareTo(new BigDecimal(0)) == 0) return null;
        return sellSum.add(dividendSum).divide(purchaseSum, 4, RoundingMode.HALF_UP).subtract(new BigDecimal(1)).multiply(new BigDecimal(100));
    }

    public void addPurchase(BigDecimal purchase)
    {
        purchaseSum = purchaseSum.add(purchase);
    }

    public void addSell(BigDecimal sell)
    {
        sellSum = sellSum.add(sell);
    }

    public void addDividend(BigDecimal dividend)
    {
        dividendSum = dividendSum.add(dividend);
    }
}
