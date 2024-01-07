package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class RecordResourceTest
{

    @Test
    void getRecords()
    {
        given().when()
                .get("/record/NVDA")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", is(1))
                .body("findAll { it.ticker == 'NVDA' }.size()", is(1));
    }

    @Test
    void parameterValidator()
    {
        assertThat(given().when()
                .get("/record/" + "AAAAAA")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Ticker Parameter"));

        assertThat(given().when()
                .get("/record/" + "AAxx")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Ticker Parameter"));
    }
}