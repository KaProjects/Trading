package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.model.Asset;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.persistence.entity.PeriodType;

import java.math.BigDecimal;
import java.math.RoundingMode;

@ApplicationScoped
public class ArithmeticService
{
    public BigDecimal purchaseTotal(BigDecimal price, BigDecimal quantity, BigDecimal fees) {
        return price.multiply(quantity).setScale(2, RoundingMode.HALF_UP).add(fees);
    }

    public BigDecimal sellTotal(BigDecimal price, BigDecimal quantity, BigDecimal fees) {
        return price.multiply(quantity).setScale(2, RoundingMode.HALF_UP).subtract(fees);
    }

    public BigDecimal profitPercentage(BigDecimal purchaseTotal, BigDecimal sellTotal) {
        if (equalsBigDecimal(purchaseTotal, BigDecimal.ZERO)) {
            return null;
        } else {
           return sellTotal
                   .divide(purchaseTotal, 4, RoundingMode.HALF_UP)
                   .subtract(new BigDecimal(1))
                   .multiply(new BigDecimal(100));
        }
    }

    public PriceIndicators.Financial computeFinancialRatios(BigDecimal marketCap, Periods.Financial financial)
    {
        if (marketCap == null || marketCap.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("marketCap must be positive non-zero number");

        PriceIndicators.Financial ratios = new PriceIndicators.Financial();
        BigDecimal revenue = financial.getRevenue().getValue();
        BigDecimal grossProfit = financial.getGrossProfit().getValue();
        BigDecimal operatingIncome = financial.getOperatingIncome().getValue();
        BigDecimal netIncome = financial.getNetIncome().getValue();
        BigDecimal freeCashFlow = financial.getFreeCashFlow().getValue();

        if (revenue != null && revenue.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setMarketCapToRevenues(limit(new BigDecimal("9999.99"), marketCap.divide(revenue, 2, RoundingMode.HALF_UP)));
        }
        if (grossProfit != null && grossProfit.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setMarketCapToGrossProfit(limit(new BigDecimal("9999.99"), marketCap.divide(grossProfit, 2, RoundingMode.HALF_UP)));
        }
        if (operatingIncome != null && operatingIncome.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setMarketCapToOperatingIncome(limit(new BigDecimal("9999.99"), marketCap.divide(operatingIncome, 2, RoundingMode.HALF_UP)));
        }
        if (netIncome != null && netIncome.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setMarketCapToNetIncome(limit(new BigDecimal("9999.99"), marketCap.divide(netIncome, 2, RoundingMode.HALF_UP)));
        }
        if (freeCashFlow != null && freeCashFlow.compareTo(BigDecimal.ZERO) > 0) {
            ratios.setMarketCapToFreeCashFlow(limit(new BigDecimal("9999.99"), marketCap.divide(freeCashFlow, 2, RoundingMode.HALF_UP)));
        }
        if (financial.getDividend() != null && financial.getDividend().compareTo(BigDecimal.ZERO) > 0) {
            ratios.setDividendYield(limit(new BigDecimal("999.99"), financial.getDividend().multiply(new BigDecimal("100")).divide(marketCap, 2, RoundingMode.HALF_UP)));
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

    public PriceIndicators computeIndicators(Latest latest, Periods.Financial ttm)
    {
        PriceIndicators indicators = new PriceIndicators();

        indicators.setDatetime(latest.getDatetime());
        indicators.setPrice(latest.getPrice());

        indicators.setShares(ttm.getShares());
        indicators.setMarketCap(indicators.getPrice().multiply(indicators.getShares()));

        indicators.setTtm(computeFinancialRatios(indicators.getMarketCap(), ttm));

        return indicators;
    }

    public boolean equalsBigDecimal(BigDecimal a, BigDecimal b)
    {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    public String shiftQuarter(String quarterId, int offset)
    {
        PeriodName quarter = PeriodName.valueOf(quarterId);
        if (!isQuarter(quarter.getType())) {
            throw new IllegalArgumentException("Period '" + quarterId + "' is not a quarter");
        }

        int absoluteQuarter = quarter.getYear().getValue() * 4
                + quarter.getType().getNumber() - 1
                + offset;
        int year = Math.floorDiv(absoluteQuarter, 4);
        int quarterNumber = Math.floorMod(absoluteQuarter, 4) + 1;
        return String.format("%02dQ%d", Math.floorMod(year, 100), quarterNumber);
    }

    private boolean isQuarter(PeriodType periodType)
    {
        return periodType == PeriodType.Q1
                || periodType == PeriodType.Q2
                || periodType == PeriodType.Q3
                || periodType == PeriodType.Q4;
    }

    private BigDecimal limit(BigDecimal max, BigDecimal value){
        return (value.compareTo(max) > 0) ? max : value;
    }
}
