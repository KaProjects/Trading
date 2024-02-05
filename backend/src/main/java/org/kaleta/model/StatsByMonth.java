package org.kaleta.model;

import lombok.Data;
import org.kaleta.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class StatsByMonth
{
    private String month;
    private int tradesCount = 0;
    private BigDecimal purchaseSum = new BigDecimal(0);
    private BigDecimal sellSum = new BigDecimal(0);
    private BigDecimal dividendSum = new BigDecimal(0);

    public StatsByMonth() {}
    public StatsByMonth(String month) {this.month = month;}

    public int compareMonthTo(StatsByMonth other)
    {
        return -Utils.compareDtoDates("01." + this.getMonth(), "01." + other.getMonth());
    }

    public BigDecimal getTradesProfit()
    {
        return sellSum.subtract(purchaseSum);
    }

    public BigDecimal getTradesProfitPercentage()
    {
        if (purchaseSum.compareTo(new BigDecimal(0)) == 0) return null;
        return sellSum.divide(purchaseSum, 4, RoundingMode.HALF_UP).subtract(new BigDecimal(1)).multiply(new BigDecimal(100));
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

    public void increaseTrade()
    {
        tradesCount++;
    }
}
