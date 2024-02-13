package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.StatsUiByCompanyDto;
import org.kaleta.dto.StatsUiByPeriodDto;
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
        Assert.get400("/stats/company?year=" + "20x2", "Invalid Year Parameter");
        Assert.get400("/stats/company?year=" + "20222", "Invalid Year Parameter");
        Assert.get400("/stats/company?year=" + "202", "Invalid Year Parameter");
        Assert.get400("/stats/company?year=", "Invalid Year Parameter");

        Assert.get400("/stats/monthly?companyId=" + "AAAAAA", "Invalid UUID Parameter");
        Assert.get400("/stats/monthly?companyId=", "Invalid UUID Parameter");

        Assert.get400("/stats/yearly?companyId=" + "AAAAAA", "Invalid UUID Parameter");
        Assert.get400("/stats/yearly?companyId=", "Invalid UUID Parameter");
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

        assertThat(dto.getColumns().size(), is(8));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getRows().size(), is(6));
        assertThat(dto.getRows().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getRows().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getRows().get(0).getPurchaseSum(), is("2028"));
        assertThat(dto.getRows().get(0).getSellSum(), is("3009.5"));
        assertThat(dto.getRows().get(0).getDividendSum(), is("0"));
        assertThat(dto.getRows().get(0).getProfit(), is("981.5"));
        assertThat(dto.getRows().get(0).getProfitUsd(), is("1079.65"));
        assertThat(dto.getRows().get(0).getProfitPercentage(), is("48.4"));
        assertThat(dto.getRows().get(2).getTicker(), is("NVDA"));
        assertThat(dto.getRows().get(2).getCurrency(), is(Currency.$));
        assertThat(dto.getRows().get(2).getPurchaseSum(), is("2017"));
        assertThat(dto.getRows().get(2).getSellSum(), is("2450"));
        assertThat(dto.getRows().get(2).getDividendSum(), is("135"));
        assertThat(dto.getRows().get(2).getProfit(), is("568"));
        assertThat(dto.getRows().get(2).getProfitUsd(), is("568"));
        assertThat(dto.getRows().get(2).getProfitPercentage(), is("28.16"));
        assertThat(dto.getSums(), is(new String[]{"6", "3", "4195", "5554.5", "1935", "3294.5", "2583.99", "78.53"}));
    }

    @Test
    void getCompaniesSortProfitPercentage()
    {
        StatsUiByCompanyDto dto = given().when()
                .get("/stats/company?sort=percentage")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", StatsUiByCompanyDto.class);

        assertThat(dto.getColumns().size(), is(8));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getRows().size(), is(6));
        assertThat(dto.getRows().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getRows().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getRows().get(0).getPurchaseSum(), is("2028"));
        assertThat(dto.getRows().get(0).getSellSum(), is("3009.5"));
        assertThat(dto.getRows().get(0).getDividendSum(), is("0"));
        assertThat(dto.getRows().get(0).getProfit(), is("981.5"));
        assertThat(dto.getRows().get(0).getProfitUsd(), is("1079.65"));
        assertThat(dto.getRows().get(0).getProfitPercentage(), is("48.4"));
        assertThat(dto.getRows().get(1).getTicker(), is("NVDA"));
        assertThat(dto.getRows().get(1).getCurrency(), is(Currency.$));
        assertThat(dto.getRows().get(1).getPurchaseSum(), is("2017"));
        assertThat(dto.getRows().get(1).getSellSum(), is("2450"));
        assertThat(dto.getRows().get(1).getDividendSum(), is("135"));
        assertThat(dto.getRows().get(1).getProfit(), is("568"));
        assertThat(dto.getRows().get(1).getProfitUsd(), is("568"));
        assertThat(dto.getRows().get(1).getProfitPercentage(), is("28.16"));
        assertThat(dto.getSums(), is(new String[]{"6", "3", "4195", "5554.5", "1935", "3294.5", "2583.99", "78.53"}));
    }

    @Test
    void getCompaniesFilterYear()
    {
        StatsUiByCompanyDto dto = given().when()
                .get("/stats/company?year=2023")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", StatsUiByCompanyDto.class);

        assertThat(dto.getColumns().size(), is(8));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getRows().size(), is(1));
        assertThat(dto.getRows().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getRows().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getRows().get(0).getPurchaseSum(), is("2028"));
        assertThat(dto.getRows().get(0).getSellSum(), is("3009.5"));
        assertThat(dto.getRows().get(0).getDividendSum(), is("0"));
        assertThat(dto.getRows().get(0).getProfit(), is("981.5"));
        assertThat(dto.getRows().get(0).getProfitPercentage(), is("48.4"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "2028", "3009.5", "0", "981.5", "1079.65", "48.4"}));
    }

    @Test
    void getMonthly()
    {
        StatsUiByPeriodDto dto = given().when()
                .get("/stats/monthly")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", StatsUiByPeriodDto.class);

        assertThat(dto.getColumns().size(), is(3));
        assertThat(dto.getColumns().get(0).getName(), is("Month"));
        assertThat(dto.getColumns().get(1).getSubColumns().size(), is(3));
        int offset = DateTime.now().getYear() == 2024 ? DateTime.now().getMonthOfYear() : 12;
        assertThat(dto.getRows().size(), is(5 * 12 + offset));
        assertThat(dto.getRows().get(11 -(12-offset)).getPeriod(), is("01.2024"));
        assertThat(dto.getRows().get(11 -(12-offset)).getTradesCount(), is("1"));
        assertThat(dto.getRows().get(11 -(12-offset)).getTradesProfit(), is("433"));
        assertThat(dto.getRows().get(11 -(12-offset)).getTradesProfitPercentage(), is("21.47"));
        assertThat(dto.getRows().get(11 -(12-offset)).getDividendSum(), is("0"));
        assertThat(dto.getRows().get(12 -(12-offset)).getPeriod(), is("12.2023"));
        assertThat(dto.getRows().get(12 -(12-offset)).getTradesCount(), is("1"));
        assertThat(dto.getRows().get(12 -(12-offset)).getTradesProfit(), is("1079.65"));
        assertThat(dto.getRows().get(12 -(12-offset)).getTradesProfitPercentage(), is("48.4"));
        assertThat(dto.getRows().get(12 -(12-offset)).getDividendSum(), is("0"));
        assertThat(dto.getRows().get(24 -(12-offset)).getPeriod(), is("12.2022"));
        assertThat(dto.getRows().get(24 -(12-offset)).getTradesCount(), is("0"));
        assertThat(dto.getRows().get(24 -(12-offset)).getTradesProfit(), is("0"));
        assertThat(dto.getRows().get(24 -(12-offset)).getTradesProfitPercentage(), is(""));
        assertThat(dto.getRows().get(24 -(12-offset)).getDividendSum(), is("72"));
        assertThat(dto.getSums(), is(new String[]{String.valueOf(5 * 12 + offset), "4", "1510.29", "35.5", "1073.7"}));
    }

    @Test
    void getMonthlyFilterCompany()
    {
        StatsUiByPeriodDto dto = given().when()
                .get("/stats/monthly?companyId=4efe9235-0c00-4b51-aa81-f2febbb65232")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", StatsUiByPeriodDto.class);

        assertThat(dto.getColumns().size(), is(3));
        assertThat(dto.getColumns().get(0).getName(), is("Month"));
        assertThat(dto.getColumns().get(1).getSubColumns().size(), is(3));
        assertThat(dto.getRows().size(), is(12));
        assertThat(dto.getRows().get(0).getPeriod(), is("12.2023"));
        assertThat(dto.getRows().get(0).getTradesCount(), is("1"));
        assertThat(dto.getRows().get(0).getTradesProfit(), is("1079.65"));
        assertThat(dto.getRows().get(0).getTradesProfitPercentage(), is("48.4"));
        assertThat(dto.getRows().get(0).getDividendSum(), is("0"));
        assertThat(dto.getSums(), is(new String[]{"12", "1", "1079.65", "48.4", "0"}));
    }

    @Test
    void getYearly()
    {
        StatsUiByPeriodDto dto = given().when()
                .get("/stats/yearly")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", StatsUiByPeriodDto.class);

        assertThat(dto.getColumns().size(), is(3));
        assertThat(dto.getColumns().get(0).getName(), is("Year"));
        assertThat(dto.getColumns().get(1).getSubColumns().size(), is(3));
        assertThat(dto.getRows().size(), is(6));
        assertThat(dto.getRows().get(0).getPeriod(), is("2024"));
        assertThat(dto.getRows().get(0).getTradesCount(), is("1"));
        assertThat(dto.getRows().get(0).getTradesProfit(), is("433"));
        assertThat(dto.getRows().get(0).getTradesProfitPercentage(), is("21.47"));
        assertThat(dto.getRows().get(0).getDividendSum(), is("0"));
        assertThat(dto.getSums(), is(new String[]{"6", "4", "1510.29", "35.5", "1073.7"}));
    }

    @Test
    void getYearlyFilterCompany()
    {
        StatsUiByPeriodDto dto = given().when()
                .get("/stats/yearly?companyId=4efe9235-0c00-4b51-aa81-f2febbb65232")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", StatsUiByPeriodDto.class);

        assertThat(dto.getColumns().size(), is(3));
        assertThat(dto.getColumns().get(0).getName(), is("Year"));
        assertThat(dto.getColumns().get(1).getSubColumns().size(), is(3));
        assertThat(dto.getRows().size(), is(1));
        assertThat(dto.getRows().get(0).getPeriod(), is("2023"));
        assertThat(dto.getRows().get(0).getTradesCount(), is("1"));
        assertThat(dto.getRows().get(0).getTradesProfit(), is("1079.65"));
        assertThat(dto.getRows().get(0).getTradesProfitPercentage(), is("48.4"));
        assertThat(dto.getRows().get(0).getDividendSum(), is("0"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "1079.65", "48.4", "0"}));
    }
}