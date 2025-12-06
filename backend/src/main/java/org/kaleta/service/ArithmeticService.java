package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.model.Asset;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;

import java.math.BigDecimal;
import java.math.RoundingMode;

@ApplicationScoped
public class ArithmeticService
{
    public PriceIndicators.Financial computeFinancialRatios(BigDecimal marketCap, Periods.Financial financial)
    {
        if (marketCap == null || marketCap.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("marketCap must be positive non-zero number");

        PriceIndicators.Financial ratios = new PriceIndicators.Financial();

        if (financial.getRevenue() != null && financial.getRevenue().compareTo(BigDecimal.ZERO) > 0) {
            ratios.setMarketCapToRevenues(marketCap.divide(financial.getRevenue(), 2, RoundingMode.HALF_UP));
        }
        if (financial.getGrossProfit() != null && financial.getGrossProfit().compareTo(BigDecimal.ZERO) > 0) {
            ratios.setMarketCapToGrossIncome(marketCap.divide(financial.getGrossProfit(), 2, RoundingMode.HALF_UP));
        }
        if (financial.getOperatingIncome() != null && financial.getOperatingIncome().compareTo(BigDecimal.ZERO) > 0) {
            ratios.setMarketCapToOperatingIncome(marketCap.divide(financial.getOperatingIncome(), 2, RoundingMode.HALF_UP));
        }
        if (financial.getNetIncome() != null && financial.getNetIncome().compareTo(BigDecimal.ZERO) > 0) {
            ratios.setMarketCapToNetIncome(marketCap.divide(financial.getNetIncome(), 2, RoundingMode.HALF_UP));
        }
        if (financial.getDividend() != null && financial.getDividend().compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDividendYield(financial.getDividend().multiply(new BigDecimal("100")).divide(marketCap, 2, RoundingMode.HALF_UP));
        }
        return ratios;
    }

    public Asset computeAsset(BigDecimal currentPrice, BigDecimal quantity, BigDecimal purchasePrice)
    {
        if (quantity == null || purchasePrice == null) return null;
        Asset asset = new Asset();
        asset.setQuantity(quantity);
        asset.setPurchasePrice(purchasePrice);

        if (currentPrice != null)
        {
            asset.setCurrentPrice(currentPrice);

            if (purchasePrice.compareTo(new BigDecimal(0)) != 0)
            {
                asset.setProfitPercent(currentPrice.divide(purchasePrice, 4, RoundingMode.HALF_UP)
                        .subtract(new BigDecimal(1)).multiply(new BigDecimal(100)));
            }

            asset.setProfitValue(currentPrice.subtract(purchasePrice).multiply(quantity));
        }
        return asset;
    }
}
