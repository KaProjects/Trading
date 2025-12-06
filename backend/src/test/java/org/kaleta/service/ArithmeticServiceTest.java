package org.kaleta.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.model.Asset;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertThat(ttmRatios.getMarketCapToRevenues(), comparesEqualTo(new BigDecimal("100")));
        assertThat(ttmRatios.getMarketCapToGrossIncome(), comparesEqualTo(new BigDecimal("200")));
        assertThat(ttmRatios.getMarketCapToOperatingIncome(), comparesEqualTo(new BigDecimal("500")));
        assertThat(ttmRatios.getMarketCapToNetIncome(), comparesEqualTo(new BigDecimal("1000")));
        assertThat(ttmRatios.getDividendYield(), comparesEqualTo(new BigDecimal("0.01")));
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
        assertThat(asset.getQuantity(), comparesEqualTo(new BigDecimal("50")));
        assertThat(asset.getPurchasePrice(), comparesEqualTo(new BigDecimal("150")));
        assertThat(asset.getCurrentPrice(), comparesEqualTo(new BigDecimal("200")));
        assertThat(asset.getProfitPercent(), comparesEqualTo(new BigDecimal("33.33")));
        assertThat(asset.getProfitValue(), comparesEqualTo(new BigDecimal("2500")));
    }

    @Test
    void computeAsset_nullCurrentPrice()
    {
        Asset asset = arithmeticService.computeAsset(null, new BigDecimal("50"), new BigDecimal("150"));
        assertThat(asset.getQuantity(), comparesEqualTo(new BigDecimal("50")));
        assertThat(asset.getPurchasePrice(), comparesEqualTo(new BigDecimal("150")));
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
