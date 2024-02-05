package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.StatsUiByCompanyDto;
import org.kaleta.entity.Currency;
import org.kaleta.framework.Assert;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class StatsResourceTest
{
    @Test
    void parameterValidator()
    {
        Assert.get400("/stats/company?currency=" + "X", "Invalid Currency Parameter");
        Assert.get400("/stats/company?currency=", "Invalid Currency Parameter");
    }

    @Test
    void getTrades()
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
    void getTradesFilterCurrency()
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
}