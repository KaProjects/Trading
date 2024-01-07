package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.TradeDto;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


@QuarkusTest
class TradeResourceTest
{
    @Test
    void getAllTrades()
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
        assertThat(dto.getTrades().size(), is(4));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), is("11.11.2023"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), is("01.11.2022"));
        assertThat(dto.getTrades().get(2).getPurchaseDate(), is("10.05.2021"));
        assertThat(dto.getTrades().get(3).getPurchaseDate(), is("05.04.2021"));
    }

    @Test
    void getActiveTrades()
    {
        TradeDto dto = given().when()
                .get("/trade/active")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradeDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(2));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), is("01.11.2022"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), is("05.04.2021"));

    }
}