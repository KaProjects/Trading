package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.StatsUiByCompanyDto;
import org.kaleta.dto.StatsUiByMonthDto;
import org.kaleta.entity.Currency;
import org.kaleta.framework.Assert;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class AStatsResourceTest
{
    @Test
    void parameterValidator()
    {
        Assert.get400("/stats/company?currency=" + "X", "Invalid Currency Parameter");
        Assert.get400("/stats/company?currency=", "Invalid Currency Parameter");

        Assert.get400("/stats/monthly?currency=" + "X", "Invalid Currency Parameter");
        Assert.get400("/stats/monthly?currency=", "Invalid Currency Parameter");
    }

    @Test
    void getCompanies()
    {
        StatsUiByCompanyDto dto = given().when()
                .get("/stats/company")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", StatsUiByCompanyDto.class);

        assertThat(dto.getColumns().size(), is(7));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getRows().size(), is(6));
        assertThat(dto.getRows().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getRows().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getRows().get(0).getPurchaseSum(), is("2028"));
        assertThat(dto.getRows().get(0).getSellSum(), is("3009.5"));
        assertThat(dto.getRows().get(0).getDividendSum(), is("0"));
        assertThat(dto.getRows().get(0).getProfit(), is("981.5"));
        assertThat(dto.getRows().get(0).getProfitPercentage(), is("48.4"));
        assertThat(dto.getRows().get(3).getTicker(), is("NVDA"));
        assertThat(dto.getRows().get(3).getCurrency(), is(Currency.$));
        assertThat(dto.getRows().get(3).getPurchaseSum(), is("2017"));
        assertThat(dto.getRows().get(3).getSellSum(), is("2450"));
        assertThat(dto.getRows().get(3).getDividendSum(), is("135"));
        assertThat(dto.getRows().get(3).getProfit(), is("568"));
        assertThat(dto.getRows().get(3).getProfitPercentage(), is("28.16"));
        assertThat(dto.getSums(), is(new String[]{"6", "3", "4195", "5554.5", "1935", "3294.5", "78.53"}));
    }

    @Test
    void getCompaniesFilterCurrency()
    {
        StatsUiByCompanyDto dto = given().when()
                .get("/stats/company?currency=€")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", StatsUiByCompanyDto.class);

        assertThat(dto.getColumns().size(), is(7));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getRows().size(), is(1));
        assertThat(dto.getRows().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getRows().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getRows().get(0).getPurchaseSum(), is("2028"));
        assertThat(dto.getRows().get(0).getSellSum(), is("3009.5"));
        assertThat(dto.getRows().get(0).getDividendSum(), is("0"));
        assertThat(dto.getRows().get(0).getProfit(), is("981.5"));
        assertThat(dto.getRows().get(0).getProfitPercentage(), is("48.4"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "2028", "3009.5", "0", "981.5", "48.4"}));
    }

    @Test
    void getMonthly()
    {
        StatsUiByMonthDto dto = given().when()
                .get("/stats/monthly")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", StatsUiByMonthDto.class);

        assertThat(dto.getColumns().size(), is(3));
        assertThat(dto.getColumns().get(1).getSubColumns().size(), is(3));
        int offset = DateTime.now().getYear() == 2024 ? DateTime.now().getMonthOfYear() : 12;
        assertThat(dto.getRows().size(), is(5 * 12 + offset));
        assertThat(dto.getRows().get(11 -(12-offset)).getMonth(), is("01.2024"));
        assertThat(dto.getRows().get(11 -(12-offset)).getTradesCount(), is("1"));
        assertThat(dto.getRows().get(11 -(12-offset)).getTradesProfit(), is("433"));
        assertThat(dto.getRows().get(11 -(12-offset)).getTradesProfitPercentage(), is("21.47"));
        assertThat(dto.getRows().get(11 -(12-offset)).getDividendSum(), is("0"));
        assertThat(dto.getRows().get(12 -(12-offset)).getMonth(), is("12.2023"));
        assertThat(dto.getRows().get(12 -(12-offset)).getTradesCount(), is("1"));
        assertThat(dto.getRows().get(12 -(12-offset)).getTradesProfit(), is("981.5"));
        assertThat(dto.getRows().get(12 -(12-offset)).getTradesProfitPercentage(), is("48.4"));
        assertThat(dto.getRows().get(12 -(12-offset)).getDividendSum(), is("0"));
        assertThat(dto.getRows().get(24 -(12-offset)).getMonth(), is("12.2022"));
        assertThat(dto.getRows().get(24 -(12-offset)).getTradesCount(), is("0"));
        assertThat(dto.getRows().get(24 -(12-offset)).getTradesProfit(), is("0"));
        assertThat(dto.getRows().get(24 -(12-offset)).getTradesProfitPercentage(), is(""));
        assertThat(dto.getRows().get(24 -(12-offset)).getDividendSum(), is("72"));
        assertThat(dto.getSums(), is(new String[]{String.valueOf(5 * 12 + offset), "4", "1359.5", "32.41", "1935"}));
    }

    @Test
    void getMonthlyFilterCurrency()
    {
        StatsUiByMonthDto dto = given().when()
                .get("/stats/monthly?currency=€")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", StatsUiByMonthDto.class);

        assertThat(dto.getColumns().size(), is(3));
        assertThat(dto.getColumns().get(1).getSubColumns().size(), is(3));
        assertThat(dto.getRows().size(), is(12));
        assertThat(dto.getRows().get(0).getMonth(), is("12.2023"));
        assertThat(dto.getRows().get(0).getTradesCount(), is("1"));
        assertThat(dto.getRows().get(0).getTradesProfit(), is("981.5"));
        assertThat(dto.getRows().get(0).getTradesProfitPercentage(), is("48.4"));
        assertThat(dto.getRows().get(0).getDividendSum(), is("0"));
    }
}