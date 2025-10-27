package org.kaleta.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Generator;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceRatios;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Latest;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class ArithmeticServiceTest
{
    @Inject
    ArithmeticService arithmeticService;

    @Test
    void computeTtmRatios()
    {
        Company company = Generator.generateCompany();
        Latest latest = Generator.generateLatest(company);
        latest.setPrice(new BigDecimal("1000"));
        Periods.Financial financial = new Periods.Financial();
        financial.setRevenue(new BigDecimal("10000"));
        financial.setGrossProfit(new BigDecimal("5000"));
        financial.setOperatingIncome(new BigDecimal("2000"));
        financial.setNetIncome(new BigDecimal("1000"));
        financial.setDividend(new BigDecimal("100"));
        financial.setShares(new BigDecimal("1000"));

        PriceRatios priceRatios = arithmeticService.computeTtmRatios(latest, financial);

        assertThat(priceRatios.getDatetime(), is(latest.getDatetime()));
        assertThat(priceRatios.getPrice(), is(latest.getPrice()));
        assertThat(priceRatios.getShares(), is(financial.getShares()));

        assertThat(priceRatios.getMarketCap(), comparesEqualTo(new BigDecimal("1000000")));
        assertThat(priceRatios.getTtm(), is(notNullValue()));

        assertThat(priceRatios.getTtm().getMarketCapToRevenues(), comparesEqualTo(new BigDecimal("100")));
        assertThat(priceRatios.getTtm().getMarketCapToGrossIncome(), comparesEqualTo(new BigDecimal("200")));
        assertThat(priceRatios.getTtm().getMarketCapToOperatingIncome(), comparesEqualTo(new BigDecimal("500")));
        assertThat(priceRatios.getTtm().getMarketCapToNetIncome(), comparesEqualTo(new BigDecimal("1000")));
        assertThat(priceRatios.getTtm().getDividendYield(), comparesEqualTo(new BigDecimal("0.01")));
    }
}
