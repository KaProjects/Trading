package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dao.TradeDao;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.kaleta.Utils.format;

@ApplicationScoped
public class TradeService
{
    @Inject
    TradeDao tradeDao;

    public List<Trade> getTrades(boolean active, String company, String currency, String year)
    {
        return tradeDao.list(active, company, currency, year);
    }

    public String[] computeSums(List<Trade> trades)
    {
        Set<String> companies = new HashSet<>();
        Set<Currency> currencies = new HashSet<>();
        BigDecimal purchaseFeesSum = new BigDecimal("0.00");
        BigDecimal purchaseTotalSum = new BigDecimal("0.00");
        BigDecimal purchaseTotalSumSold = new BigDecimal("0.00");
        BigDecimal sellFeesSum = new BigDecimal("0.00");
        BigDecimal sellTotalSum = new BigDecimal("0.00");
        for (Trade trade : trades)
        {
            companies.add(trade.getTicker());
            currencies.add(trade.getCurrency());
            purchaseFeesSum = purchaseFeesSum.add(trade.getPurchaseFees());
            purchaseTotalSum = purchaseTotalSum.add(trade.getPurchaseTotal());
            if (trade.getSellDate() != null)
            {
                purchaseTotalSumSold = purchaseTotalSumSold.add(trade.getPurchaseTotal());
                sellFeesSum = sellFeesSum.add(trade.getSellFees());
                sellTotalSum = sellTotalSum.add(trade.getSellTotal());
            }
        }
        String profit = "";
        String profitPercentage = "";
        if (!purchaseTotalSumSold.equals(new BigDecimal("0.00"))){
            profit = format(sellTotalSum.subtract(purchaseTotalSumSold));
            profitPercentage = format(sellTotalSum.divide(purchaseTotalSumSold, 4, RoundingMode.HALF_UP).subtract(new BigDecimal(1)).multiply(new BigDecimal(100)));
        }
        return new String[]{String.valueOf(companies.size()), String.valueOf(currencies.size()), "", "", "",
                format(purchaseFeesSum), format(purchaseTotalSum), "", "", "",
                format(sellFeesSum), format(sellTotalSum), profit, profitPercentage};
    }
}
