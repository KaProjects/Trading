package org.kaleta.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.model.Asset;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
public class ArithmeticServiceTest
{
    @Inject
    ArithmeticService arithmeticService;

    @Test
    void computeFinancialRatios()
    {
        BigDecimal marketCap = new BigDecimal("1000000");

        Periods.Financial financial = new Periods.Financial();
        financial.setRevenue(new BigDecimal("10000"));
        financial.setGrossProfit(new BigDecimal("5000"));
        financial.setOperatingIncome(new BigDecimal("2000"));
        financial.setNetIncome(new BigDecimal("1000"));
        financial.setDividend(new BigDecimal("100"));

        PriceIndicators.Financial ttmRatios = arithmeticService.computeFinancialRatios(marketCap, financial);

        assertBigDecimals(ttmRatios.getMarketCapToRevenues(), new BigDecimal("100"));
        assertBigDecimals(ttmRatios.getMarketCapToGrossIncome(), new BigDecimal("200"));
        assertBigDecimals(ttmRatios.getMarketCapToOperatingIncome(), new BigDecimal("500"));
        assertBigDecimals(ttmRatios.getMarketCapToNetIncome(), new BigDecimal("1000"));
        assertBigDecimals(ttmRatios.getDividendYield(), new BigDecimal("0.01"));
    }

    @Test
    void computeFinancialRatios_invalidMarketCap()
    {
        assertThrows(IllegalArgumentException.class, () -> arithmeticService.computeFinancialRatios(new BigDecimal("0"), new Periods.Financial()));
        assertThrows(IllegalArgumentException.class, () -> arithmeticService.computeFinancialRatios(new BigDecimal("-1"), new Periods.Financial()));
    }

    @Test
    void computeFinancialRatios_invalidFinancials()
    {
        BigDecimal marketCap = new BigDecimal("1000000");

        Periods.Financial financial = new Periods.Financial();
        financial.setRevenue(new BigDecimal("-10000"));
        financial.setGrossProfit(new BigDecimal("-5000"));
        financial.setOperatingIncome(new BigDecimal("-2000"));
        financial.setNetIncome(new BigDecimal("-1000"));
        financial.setDividend(new BigDecimal("-100"));

        PriceIndicators.Financial ttmRatios = arithmeticService.computeFinancialRatios(marketCap, financial);

        assertThat(ttmRatios.getMarketCapToRevenues(), is(nullValue()));
        assertThat(ttmRatios.getMarketCapToGrossIncome(), is(nullValue()));
        assertThat(ttmRatios.getMarketCapToOperatingIncome(), is(nullValue()));
        assertThat(ttmRatios.getMarketCapToNetIncome(), is(nullValue()));
        assertThat(ttmRatios.getDividendYield(), is(nullValue()));
    }

    @Test
    void computeAsset()
    {
        Asset asset = arithmeticService.computeAsset(new BigDecimal("200"), new BigDecimal("50"), new BigDecimal("150"));
        assertBigDecimals(asset.getQuantity(), new BigDecimal("50"));
        assertBigDecimals(asset.getPurchasePrice(), new BigDecimal("150"));
        assertBigDecimals(asset.getCurrentPrice(), new BigDecimal("200"));
        assertBigDecimals(asset.getProfitPercent(), new BigDecimal("33.33"));
        assertBigDecimals(asset.getProfitValue(), new BigDecimal("2500"));
    }

    @Test
    void computeAsset_nullCurrentPrice()
    {
        Asset asset = arithmeticService.computeAsset(null, new BigDecimal("50"), new BigDecimal("150"));
        assertBigDecimals(asset.getQuantity(), new BigDecimal("50"));
        assertBigDecimals(asset.getPurchasePrice(), new BigDecimal("150"));
        assertThat(asset.getCurrentPrice(), is(nullValue()));
        assertThat(asset.getProfitPercent(), is(nullValue()));
        assertThat(asset.getProfitValue(), is(nullValue()));
    }

    @Test
    void computeAsset_noPurchase()
    {
        Asset asset = arithmeticService.computeAsset(new BigDecimal("200"), null, null);
        assertThat(asset, is(nullValue()));
    }
}
