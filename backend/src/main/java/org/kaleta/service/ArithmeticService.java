package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceRatios;
import org.kaleta.persistence.entity.Latest;

import java.math.BigDecimal;
import java.math.RoundingMode;

@ApplicationScoped
public class ArithmeticService
{
    public PriceRatios computeTtmRatios(Latest latest, Periods.Financial ttm) {
        PriceRatios ratios = new PriceRatios();

        ratios.setDatetime(latest.getDatetime());
        ratios.setPrice(latest.getPrice());
        ratios.setShares(ttm.getShares());

        ratios.setMarketCap(ratios.getPrice().multiply(ratios.getShares()));

        ratios.setTtm(new PriceRatios.Financial());

        ratios.getTtm().setMarketCapToRevenues(ratios.getMarketCap().divide(ttm.getRevenue(), 2, RoundingMode.HALF_UP));
        ratios.getTtm().setMarketCapToGrossIncome(ratios.getMarketCap().divide(ttm.getGrossProfit(), 2, RoundingMode.HALF_UP));
        ratios.getTtm().setMarketCapToOperatingIncome(ratios.getMarketCap().divide(ttm.getOperatingIncome(), 2, RoundingMode.HALF_UP));
        ratios.getTtm().setMarketCapToNetIncome(ratios.getMarketCap().divide(ttm.getNetIncome(), 2, RoundingMode.HALF_UP));
        ratios.getTtm().setDividendYield(ttm.getDividend().multiply(new BigDecimal("100")).divide(ratios.getMarketCap(), 2, RoundingMode.HALF_UP));

        return ratios;
    }
}
