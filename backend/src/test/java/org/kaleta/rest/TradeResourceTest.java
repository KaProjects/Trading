package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.TradeDto;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;


@QuarkusTest
class TradeResourceTest
{
    @Test
    void getTradesFilterNone()
    {
        TradeDto dto = given().when()
                .get("/trade")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradeDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(6));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), is("11.11.2023"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), is("05.04.2023"));
        assertThat(dto.getTrades().get(2).getPurchaseDate(), is("01.11.2022"));
        assertThat(dto.getTrades().get(3).getPurchaseDate(), is("10.05.2021"));
        assertThat(dto.getSums(), is(new String[]{"6", "4", "", "", "", "357.62", "2579819.35", "", "", "", "85.5", "5725.5", "1530.5", "36.48"}));
    }

    @Test
    void getTradesFilterActive()
    {
        TradeDto dto = given().when()
                .get("/trade?active=true")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradeDto.class);
        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(2));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), is("05.04.2023"));
        assertThat(dto.getTrades().get(0).getTicker(), is("CEZ"));
        assertThat(dto.getTrades().get(0).getPurchaseTotal(), is("575599.35"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), is("01.11.2022"));
        assertThat(dto.getTrades().get(1).getTicker(), is("RR"));
        assertThat(dto.getTrades().get(1).getPurchaseTotal(), is("2000025"));
        assertThat(dto.getSums(), is(new String[]{"2", "2", "", "", "", "275.12", "2575624.35", "", "", "", "0", "0", "", ""}));
    }

    @Test
    void getTradesFilterCurrency()
    {
        TradeDto dto = given().when()
                .get("/trade?currency=€")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradeDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "", "", "18", "2028", "", "", "", "30.5", "3070.5", "1042.5", "51.41"}));
    }

    @Test
    void getTradesFilterCompany()
    {
        TradeDto dto = given().when()
                .get("/trade?company=NVDA")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradeDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getTicker(), is("NVDA"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "", "", "14.5", "2017", "", "", "", "50", "2550", "533", "26.43"}));
    }

    @Test
    void getTradesFilterYear()
    {
        TradeDto dto = given().when()
                .get("/trade?year=2023")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradeDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(3));
        assertThat(dto.getTrades().get(0).getTicker(), is("NVDA"));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), endsWith("2023"));
        assertThat(dto.getTrades().get(1).getTicker(), is("CEZ"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), endsWith("2023"));
        assertThat(dto.getTrades().get(2).getTicker(), is("SHELL"));
        assertThat(dto.getTrades().get(2).getSellDate(), endsWith("2023"));
        assertThat(dto.getSums(), is(new String[]{"3", "3", "", "", "", "282.62", "579644.35", "", "", "", "80.5", "5620.5", "1575.5", "38.95"}));

    }

    @Test
    void getTradesFilterMultiple()
    {
        TradeDto dto = given().when()
                .get("/trade?year=2023&company=CEZ")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradeDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getTicker(), is("CEZ"));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), endsWith("2023"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "", "", "250.12", "575599.35", "", "", "", "0", "0", "", ""}));
    }

    @Test
    void getTradesZeroTotals()
    {
        TradeDto dtoZeroPurchase = given().when()
                .get("/trade?company=XXX")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradeDto.class);

        assertThat(dtoZeroPurchase.getTrades().size(), is(1));
        assertThat(dtoZeroPurchase.getTrades().get(0).getTicker(), is("XXX"));
        assertThat(dtoZeroPurchase.getTrades().get(0).getPurchaseTotal(), is("0"));
        assertThat(dtoZeroPurchase.getSums(), is(new String[]{"1", "1", "", "", "", "0", "0", "", "", "", "5", "105", "", ""}));

        TradeDto dtoZeroSell = given().when()
                .get("/trade?company=YYY")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradeDto.class);

        assertThat(dtoZeroSell.getTrades().size(), is(1));
        assertThat(dtoZeroSell.getTrades().get(0).getTicker(), is("YYY"));
        assertThat(dtoZeroSell.getTrades().get(0).getSellTotal(), is("0"));
        assertThat(dtoZeroSell.getSums(), is(new String[]{"1", "1", "", "", "", "50", "150", "", "", "", "0", "0", "-150", "-100"}));
    }

    @Test
    void parameterValidator()
    {
        assertThat(given().when()
                .get("/trade?company=" + "AAAAAA")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Ticker Parameter"));

        assertThat(given().when()
                .get("/trade?company=" + "AAxx")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Ticker Parameter"));

        assertThat(given().when()
                .get("/trade?company=")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Ticker Parameter"));

        assertThat(given().when()
                .get("/trade?currency=" + "X")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Currency Parameter"));

        assertThat(given().when()
                .get("/trade?currency=")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Currency Parameter"));

        assertThat(given().when()
                .get("/trade?year=" + "20x2")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Year Parameter"));

        assertThat(given().when()
                .get("/trade?year=" + "20222")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Year Parameter"));

        assertThat(given().when()
                .get("/trade?year=" + "202")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Year Parameter"));

        assertThat(given().when()
                .get("/trade?year=")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Year Parameter"));
    }
}