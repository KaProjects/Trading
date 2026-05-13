package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.model.PeriodStats;
import org.kaleta.framework.Assert;
import org.kaleta.model.CompanyStats;
import org.kaleta.persistence.entity.Currency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_UUID;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
class AStatsEndpointsTest
{
    String path = "/stats";

    @Test
    void getCompanies()
    {
        CompanyStats dto = given().when()
                .get(path + "/company")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyStats.class);

        assertThat(dto.getCompanies().size(), is(6));
        assertThat(dto.getYears().size(), is(6));
        assertThat(dto.getYears(), hasItems("2024", "2023", "2022", "2021", "2020", "2018"));
        assertThat(dto.getCompanies().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getCompanies().get(5).getTicker(), is("YYY"));

        CompanyStats.Company shell = findCompany(dto, "SHELL");
        assertThat(shell.getCurrency(), is(Currency.€));
        assertBigDecimals(shell.getPurchaseSum(), new java.math.BigDecimal("2028"));
        assertBigDecimals(shell.getSellSum(), new java.math.BigDecimal("3009.5"));
        assertBigDecimals(shell.getDividendSum(), new java.math.BigDecimal("0"));
        assertBigDecimals(shell.getProfitSum(), new java.math.BigDecimal("981.5"));
        assertBigDecimals(shell.getProfitUsdSum(), new java.math.BigDecimal("1079.65"));
        assertBigDecimals(shell.getProfitPercentage(), new java.math.BigDecimal("48.4"));

        CompanyStats.Company nvda = findCompany(dto, "NVDA");
        assertThat(nvda.getCurrency(), is(Currency.$));
        assertBigDecimals(nvda.getPurchaseSum(), new java.math.BigDecimal("2017"));
        assertBigDecimals(nvda.getSellSum(), new java.math.BigDecimal("2450"));
        assertBigDecimals(nvda.getDividendSum(), new java.math.BigDecimal("135"));
        assertBigDecimals(nvda.getProfitSum(), new java.math.BigDecimal("568"));
        assertBigDecimals(nvda.getProfitUsdSum(), new java.math.BigDecimal("568"));
        assertBigDecimals(nvda.getProfitPercentage(), new java.math.BigDecimal("28.16"));

        assertThat(dto.getAggregates().getCompanies(), is(6));
        assertThat(dto.getAggregates().getCurrencies(), is(3));
        assertBigDecimals(dto.getAggregates().getPurchaseSum(), new java.math.BigDecimal("4195"));
        assertBigDecimals(dto.getAggregates().getSellSum(), new java.math.BigDecimal("5554.5"));
        assertBigDecimals(dto.getAggregates().getDividendSum(), new java.math.BigDecimal("1935"));
        assertBigDecimals(dto.getAggregates().getProfitSum(), new java.math.BigDecimal("3294.5"));
        assertBigDecimals(dto.getAggregates().getProfitSumUsd(), new java.math.BigDecimal("2583.99"));
        assertBigDecimals(dto.getAggregates().getProfitPercentage(), new java.math.BigDecimal("78.53"));
    }

    @Test
    void getCompanies_invalidParameters()
    {
        Assert.getValidationError(path + "/company?year=20x2", "must match YYYY");
        Assert.getValidationError(path + "/company?year=20222", "must match YYYY");
        Assert.getValidationError(path + "/company?year=202", "must match YYYY");
        Assert.getValidationError(path + "/company?year=", "must match YYYY");
        Assert.getValidationError(path + "/company?sort=-1", "must be any of Sort");
        Assert.getValidationError(path + "/company?sort=8", "must be any of Sort");
        Assert.getValidationError(path + "/company?sort=", "must be any of Sort");
        Assert.getValidationError(path + "/company?sort=X", "must be any of Sort");
        Assert.getValidationError(path + "/company?sector=X", "must be any of Sector");
        Assert.getValidationError(path + "/company?sector=", "must be any of Sector");
    }

    @Test
    void getMonthly()
    {
        PeriodStats dto = given().when()
                .get(path + "/monthly")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", PeriodStats.class);

        assertThat(dto.getPeriods().size(), is(72));

        PeriodStats.Period january2024 = findPeriod(dto, "2024-01");
        assertThat(january2024.getTradesCount(), is(1));
        assertBigDecimals(january2024.getTradesPurchaseSum(), new java.math.BigDecimal("2017"));
        assertBigDecimals(january2024.getTradesSellSum(), new java.math.BigDecimal("2450"));
        assertBigDecimals(january2024.getTradesProfitSum(), new java.math.BigDecimal("433"));
        assertBigDecimals(january2024.getTradesProfitPercentage(), new java.math.BigDecimal("21.47"));
        assertBigDecimals(january2024.getDividendSum(), new java.math.BigDecimal("0"));

        PeriodStats.Period december2023 = findPeriod(dto, "2023-12");
        assertThat(december2023.getTradesCount(), is(1));
        assertBigDecimals(december2023.getTradesPurchaseSum(), new java.math.BigDecimal("2028"));
        assertBigDecimals(december2023.getTradesSellSum(), new java.math.BigDecimal("3009.5"));
        assertBigDecimals(december2023.getTradesProfitSum(), new java.math.BigDecimal("981.5"));
        assertBigDecimals(december2023.getTradesProfitPercentage(), new java.math.BigDecimal("48.4"));
        assertBigDecimals(december2023.getDividendSum(), new java.math.BigDecimal("0"));

        PeriodStats.Period december2022 = findPeriod(dto, "2022-12");
        assertThat(december2022.getTradesCount(), is(0));
        assertBigDecimals(december2022.getTradesPurchaseSum(), new java.math.BigDecimal("0"));
        assertBigDecimals(december2022.getTradesSellSum(), new java.math.BigDecimal("0"));
        assertBigDecimals(december2022.getTradesProfitSum(), new java.math.BigDecimal("0"));
        assertThat(december2022.getTradesProfitPercentage(), is(nullValue()));
        assertBigDecimals(december2022.getDividendSum(), new java.math.BigDecimal("72"));

        assertThat(dto.getAggregates().getPeriods(), is(72));
        assertThat(dto.getAggregates().getTradesCount(), is(4));
        assertBigDecimals(dto.getAggregates().getTradesProfitSum(), new java.math.BigDecimal("1359.5"));
        assertBigDecimals(dto.getAggregates().getTradesProfitPercentage(), new java.math.BigDecimal("32.4100"));
        assertBigDecimals(dto.getAggregates().getDividendSum(), new java.math.BigDecimal("1935"));
    }

    @Test
    void getMonthly_invalidParameters()
    {
        Assert.getValidationError(path + "/monthly?companyId=AAAAAA", VALID_UUID);
        Assert.getValidationError(path + "/monthly?companyId=", VALID_UUID);
        Assert.getValidationError(path + "/monthly?sector=X", "must be any of Sector");
        Assert.getValidationError(path + "/monthly?sector=", "must be any of Sector");
    }

    @Test
    void getYearly()
    {
        PeriodStats dto = given().when()
                .get(path + "/yearly")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", PeriodStats.class);

        assertThat(dto.getPeriods().size(), is(6));

        PeriodStats.Period year2024 = findPeriod(dto, "2024");
        assertThat(year2024.getTradesCount(), is(1));
        assertBigDecimals(year2024.getTradesPurchaseSum(), new java.math.BigDecimal("2017"));
        assertBigDecimals(year2024.getTradesSellSum(), new java.math.BigDecimal("2450"));
        assertBigDecimals(year2024.getTradesProfitSum(), new java.math.BigDecimal("433"));
        assertBigDecimals(year2024.getTradesProfitPercentage(), new java.math.BigDecimal("21.47"));
        assertBigDecimals(year2024.getDividendSum(), new java.math.BigDecimal("0"));

        PeriodStats.Period year2021 = findPeriod(dto, "2021");
        assertThat(year2021.getTradesCount(), is(0));
        assertBigDecimals(year2021.getTradesPurchaseSum(), new java.math.BigDecimal("0"));
        assertBigDecimals(year2021.getTradesSellSum(), new java.math.BigDecimal("0"));
        assertBigDecimals(year2021.getTradesProfitSum(), new java.math.BigDecimal("0"));
        assertThat(year2021.getTradesProfitPercentage(), is(nullValue()));
        assertBigDecimals(year2021.getDividendSum(), new java.math.BigDecimal("963"));

        assertThat(dto.getAggregates().getPeriods(), is(6));
        assertThat(dto.getAggregates().getTradesCount(), is(4));
        assertBigDecimals(dto.getAggregates().getTradesProfitSum(), new java.math.BigDecimal("1359.5"));
        assertBigDecimals(dto.getAggregates().getTradesProfitPercentage(), new java.math.BigDecimal("32.4100"));
        assertBigDecimals(dto.getAggregates().getDividendSum(), new java.math.BigDecimal("1935"));
    }

    @Test
    void getQuarterly()
    {
        PeriodStats dto = given().when()
                .get(path + "/quarterly")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", PeriodStats.class);

        assertThat(dto.getPeriods().size(), is(24));

        PeriodStats.Period quarter2024Q1 = findPeriod(dto, "2024-Q1");
        assertThat(quarter2024Q1.getTradesCount(), is(1));
        assertBigDecimals(quarter2024Q1.getTradesPurchaseSum(), new java.math.BigDecimal("2017"));
        assertBigDecimals(quarter2024Q1.getTradesSellSum(), new java.math.BigDecimal("2450"));
        assertBigDecimals(quarter2024Q1.getTradesProfitSum(), new java.math.BigDecimal("433"));
        assertBigDecimals(quarter2024Q1.getTradesProfitPercentage(), new java.math.BigDecimal("21.47"));
        assertBigDecimals(quarter2024Q1.getDividendSum(), new java.math.BigDecimal("0"));

        PeriodStats.Period quarter2018Q2 = findPeriod(dto, "2018-Q2");
        assertThat(quarter2018Q2.getTradesCount(), is(2));
        assertBigDecimals(quarter2018Q2.getTradesPurchaseSum(), new java.math.BigDecimal("150"));
        assertBigDecimals(quarter2018Q2.getTradesSellSum(), new java.math.BigDecimal("95"));
        assertBigDecimals(quarter2018Q2.getTradesProfitSum(), new java.math.BigDecimal("-55"));
        assertBigDecimals(quarter2018Q2.getTradesProfitPercentage(), new java.math.BigDecimal("-36.67"));
        assertBigDecimals(quarter2018Q2.getDividendSum(), new java.math.BigDecimal("0"));

        PeriodStats.Period quarter2021Q4 = findPeriod(dto, "2021-Q4");
        assertThat(quarter2021Q4.getTradesCount(), is(0));
        assertBigDecimals(quarter2021Q4.getTradesPurchaseSum(), new java.math.BigDecimal("0"));
        assertBigDecimals(quarter2021Q4.getTradesSellSum(), new java.math.BigDecimal("0"));
        assertBigDecimals(quarter2021Q4.getTradesProfitSum(), new java.math.BigDecimal("0"));
        assertThat(quarter2021Q4.getTradesProfitPercentage(), is(nullValue()));
        assertBigDecimals(quarter2021Q4.getDividendSum(), new java.math.BigDecimal("900"));

        assertThat(dto.getAggregates().getPeriods(), is(24));
        assertThat(dto.getAggregates().getTradesCount(), is(4));
        assertBigDecimals(dto.getAggregates().getTradesProfitSum(), new java.math.BigDecimal("1359.5"));
        assertBigDecimals(dto.getAggregates().getTradesProfitPercentage(), new java.math.BigDecimal("32.4100"));
        assertBigDecimals(dto.getAggregates().getDividendSum(), new java.math.BigDecimal("1935"));
    }

    @Test
    void getQuarterly_invalidParameters()
    {
        Assert.getValidationError(path + "/quarterly?companyId=AAAAAA", VALID_UUID);
        Assert.getValidationError(path + "/quarterly?companyId=", VALID_UUID);
        Assert.getValidationError(path + "/quarterly?sector=X", "must be any of Sector");
        Assert.getValidationError(path + "/quarterly?sector=", "must be any of Sector");
    }

    @Test
    void getYearly_invalidParameters()
    {
        Assert.getValidationError(path + "/yearly?companyId=AAAAAA", VALID_UUID);
        Assert.getValidationError(path + "/yearly?companyId=", VALID_UUID);
        Assert.getValidationError(path + "/yearly?sector=X", "must be any of Sector");
        Assert.getValidationError(path + "/yearly?sector=", "must be any of Sector");
    }

    private static CompanyStats.Company findCompany(CompanyStats dto, String ticker)
    {
        return dto.getCompanies().stream()
                .filter(company -> company.getTicker().equals(ticker))
                .findFirst()
                .orElseThrow();
    }

    private static PeriodStats.Period findPeriod(PeriodStats dto, String period)
    {
        return dto.getPeriods().stream()
                .filter(stats -> stats.getPeriod().equals(period))
                .findFirst()
                .orElseThrow();
    }
}
