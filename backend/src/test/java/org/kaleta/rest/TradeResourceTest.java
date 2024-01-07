package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;


@QuarkusTest
class TradeResourceTest
{
    @Test
    void getAllTrades()
    {
        given().when()
                .get("/trade")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", is(4))
                .body("findAll { it.ticker == 'NVDA' }.size()", is(1))
                .body("findAll { it.ticker == 'CEZ' }.size()", is(1))
                .body("findAll { it.ticker == 'RR' }.size()", is(1))
                .body("findAll { it.ticker == 'SHELL' }.size()", is(1));
    }

    @Test
    void getActiveTrades()
    {
        given().when()
                .get("/trade/active")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", is(2))
                .body("findAll { it.ticker == 'CEZ' }.size()", is(1))
                .body("findAll { it.ticker == 'RR' }.size()", is(1));
    }
}