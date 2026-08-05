package org.kaleta.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Generator;
import org.kaleta.model.Asset;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Latest;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
public class ArithmeticServiceTest
{
    @Inject
    ArithmeticService arithmeticService;

    @Test
    void purchaseTotal()
    {
        BigDecimal total = arithmeticService.purchaseTotal(new BigDecimal("12.345"), new BigDecimal("2.1"), new BigDecimal("1.23"));

        assertBigDecimals(total, new BigDecimal("27.15"));
    }

    @Test
    void sellTotal()
    {
        BigDecimal total = arithmeticService.sellTotal(new BigDecimal("12.345"), new BigDecimal("2.1"), new BigDecimal("1.23"));

        assertBigDecimals(total, new BigDecimal("24.69"));
    }

    @Test
    void profitPercentage()
    {
        assertBigDecimals(arithmeticService.profitPercentage(new BigDecimal("200"), new BigDecimal("250")), new BigDecimal("25.00"));
        assertBigDecimals(arithmeticService.profitPercentage(new BigDecimal("200"), new BigDecimal("150")), new BigDecimal("-25.00"));
        assertThat(arithmeticService.profitPercentage(BigDecimal.ZERO, new BigDecimal("150")), is(nullValue()));
        assertThat(arithmeticService.profitPercentage(new BigDecimal("0.00"), new BigDecimal("150")), is(nullValue()));
    }

    @Test
    void computeFinancialRatios()
    {
        BigDecimal marketCap = new BigDecimal("1000000");

        Periods.Financial financial = new Periods.Financial();
        financial.getRevenue().setValue(new BigDecimal("10000"));
        financial.getGrossProfit().setValue(new BigDecimal("5000"));
        financial.getOperatingIncome().setValue(new BigDecimal("2000"));
        financial.getNetIncome().setValue(new BigDecimal("1000"));
        financial.setDividend(new BigDecimal("100"));

        PriceIndicators.Financial ttmRatios = arithmeticService.computeFinancialRatios(marketCap, financial);

        assertBigDecimals(ttmRatios.getMarketCapToRevenues(), new BigDecimal("100"));
        assertBigDecimals(ttmRatios.getMarketCapToGrossProfit(), new BigDecimal("200"));
        assertBigDecimals(ttmRatios.getMarketCapToOperatingIncome(), new BigDecimal("500"));
        assertBigDecimals(ttmRatios.getMarketCapToNetIncome(), new BigDecimal("1000"));
        assertBigDecimals(ttmRatios.getDividendYield(), new BigDecimal("0.01"));
    }

    @Test
    void computeFinancialRatios_invalidMarketCap()
    {
        assertThrows(IllegalArgumentException.class, () -> arithmeticService.computeFinancialRatios(null, new Periods.Financial()));
        assertThrows(IllegalArgumentException.class, () -> arithmeticService.computeFinancialRatios(new BigDecimal("0"), new Periods.Financial()));
        assertThrows(IllegalArgumentException.class, () -> arithmeticService.computeFinancialRatios(new BigDecimal("-1"), new Periods.Financial()));
    }

    @Test
    void computeFinancialRatios_invalidFinancials()
    {
        BigDecimal marketCap = new BigDecimal("1000000");

        Periods.Financial financial = new Periods.Financial();
        financial.getRevenue().setValue(new BigDecimal("-10000"));
        financial.getGrossProfit().setValue(new BigDecimal("-5000"));
        financial.getOperatingIncome().setValue(new BigDecimal("-2000"));
        financial.getNetIncome().setValue(new BigDecimal("-1000"));
        financial.setDividend(new BigDecimal("-100"));

        PriceIndicators.Financial ttmRatios = arithmeticService.computeFinancialRatios(marketCap, financial);

        assertThat(ttmRatios.getMarketCapToRevenues(), is(nullValue()));
        assertThat(ttmRatios.getMarketCapToGrossProfit(), is(nullValue()));
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

    @Test
    void computeIndicators()
    {
        Company company = Generator.generateCompany();
        Latest latest = Generator.generateLatest(company);
        Periods.Financial ttm = Generator.generatePeriodsFinancial();

        PriceIndicators indicators = arithmeticService.computeIndicators(latest, ttm);

        assertThat(indicators.getDatetime(), is(latest.getDatetime()));
        assertBigDecimals(indicators.getPrice(), latest.getPrice());
        assertBigDecimals(indicators.getShares(), ttm.getShares());
        assertBigDecimals(indicators.getMarketCap(), latest.getPrice().multiply(ttm.getShares()));

        PriceIndicators.Financial expected = arithmeticService.computeFinancialRatios(indicators.getMarketCap(), ttm);

        assertThat(indicators.getTtm(), is(notNullValue()));
        assertBigDecimals(indicators.getTtm().getMarketCapToRevenues(), expected.getMarketCapToRevenues());
        assertBigDecimals(indicators.getTtm().getMarketCapToGrossProfit(), expected.getMarketCapToGrossProfit());
        assertBigDecimals(indicators.getTtm().getMarketCapToOperatingIncome(), expected.getMarketCapToOperatingIncome());
        assertBigDecimals(indicators.getTtm().getMarketCapToNetIncome(), expected.getMarketCapToNetIncome());
        assertBigDecimals(indicators.getTtm().getDividendYield(), expected.getDividendYield());
    }

    @Test
    void computeIndicators_invalidFinancials()
    {
        Company company = Generator.generateCompany();
        Latest latest = Generator.generateLatest(company);
        Periods.Financial ttm = Generator.generatePeriodsFinancial();
        ttm.getRevenue().setValue(new BigDecimal("-10000"));
        ttm.getGrossProfit().setValue(new BigDecimal("-5000"));
        ttm.getOperatingIncome().setValue(new BigDecimal("-2000"));
        ttm.getNetIncome().setValue(new BigDecimal("-1000"));
        ttm.setDividend(new BigDecimal("-100"));

        PriceIndicators indicators = arithmeticService.computeIndicators(latest, ttm);

        assertThat(indicators.getDatetime(), is(latest.getDatetime()));
        assertBigDecimals(indicators.getPrice(), latest.getPrice());
        assertBigDecimals(indicators.getShares(), ttm.getShares());
        assertBigDecimals(indicators.getMarketCap(), latest.getPrice().multiply(ttm.getShares()));

        assertThat(indicators.getTtm(), is(notNullValue()));
        assertBigDecimals(indicators.getTtm().getMarketCapToRevenues(), null);
        assertBigDecimals(indicators.getTtm().getMarketCapToGrossProfit(), null);
        assertBigDecimals(indicators.getTtm().getMarketCapToOperatingIncome(), null);
        assertBigDecimals(indicators.getTtm().getMarketCapToNetIncome(), null);
        assertBigDecimals(indicators.getTtm().getDividendYield(), null);
    }

    @Test
    void computeIndicators_limits()
    {
        Company company = Generator.generateCompany();
        Latest latest = Generator.generateLatest(company);
        Periods.Financial ttm = Generator.generatePeriodsFinancial();

        latest.setPrice(new BigDecimal("10000"));
        ttm.setShares(new BigDecimal("10000"));
        ttm.getRevenue().setValue(new BigDecimal("100"));
        ttm.getGrossProfit().setValue(new BigDecimal("80"));
        ttm.getOperatingIncome().setValue(new BigDecimal("60"));
        ttm.getNetIncome().setValue(new BigDecimal("40"));
        ttm.setDividend(new BigDecimal("2000000000"));

        PriceIndicators indicators = arithmeticService.computeIndicators(latest, ttm);

        assertThat(indicators.getDatetime(), is(latest.getDatetime()));
        assertBigDecimals(indicators.getPrice(), latest.getPrice());
        assertBigDecimals(indicators.getShares(), ttm.getShares());
        assertBigDecimals(indicators.getMarketCap(), latest.getPrice().multiply(ttm.getShares()));

        assertThat(indicators.getTtm(), is(notNullValue()));
        assertBigDecimals(indicators.getTtm().getMarketCapToRevenues(), new BigDecimal("9999.99"));
        assertBigDecimals(indicators.getTtm().getMarketCapToGrossProfit(), new BigDecimal("9999.99"));
        assertBigDecimals(indicators.getTtm().getMarketCapToOperatingIncome(), new BigDecimal("9999.99"));
        assertBigDecimals(indicators.getTtm().getMarketCapToNetIncome(), new BigDecimal("9999.99"));
        assertBigDecimals(indicators.getTtm().getDividendYield(), new BigDecimal("999.99"));
    }

    @Test
    void equalsBigDecimal()
    {
        assertThat(arithmeticService.equalsBigDecimal(new BigDecimal("1.0"), new BigDecimal("1.00")), is(true));
        assertThat(arithmeticService.equalsBigDecimal(new BigDecimal("1.0"), new BigDecimal("2.0")), is(false));
        assertThat(arithmeticService.equalsBigDecimal(null, new BigDecimal("1.0")), is(false));
        assertThat(arithmeticService.equalsBigDecimal(new BigDecimal("1.0"), null), is(false));
        assertThat(arithmeticService.equalsBigDecimal(null, null), is(true));
    }

    @Test
    void shiftQuarter()
    {
        assertThat(arithmeticService.shiftQuarter("26Q1", -1), is("25Q4"));
        assertThat(arithmeticService.shiftQuarter("26Q1", -4), is("25Q1"));
        assertThat(arithmeticService.shiftQuarter("26Q4", 1), is("27Q1"));
        assertThat(arithmeticService.shiftQuarter("26Q4", 3), is("27Q3"));
        assertThat(arithmeticService.shiftQuarter("00Q1", -1), is("99Q4"));
    }

    @Test
    void shiftQuarter_rejectsNonQuarterPeriod()
    {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> arithmeticService.shiftQuarter("26FY", 1));

        assertThat(exception.getMessage(), is("Period '26FY' is not a quarter"));
    }
}
