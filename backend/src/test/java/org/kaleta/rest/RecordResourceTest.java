package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.RecordDto;
import org.kaleta.entity.Currency;

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
        RecordDto dto = given().when()
                .get("/record/adb89a0a-86bc-4854-8a55-058ad2e6308f")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordDto.class);

        assertThat(dto.getTicker(), is("NVDA"));
        assertThat(dto.getCurrency(), is(Currency.$));
        assertThat(dto.getRecords().size(), is(2));
        assertThat(dto.getRecords().get(0).getDate(), is("05.01.2024"));
        assertThat(dto.getRecords().get(1).getDate(), is("11.11.2023"));
    }

    @Test
    void parameterValidator()
    {
        assertThat(given().when()
                .get("/record/" + "AAAAAA")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid UUID Parameter"));
    }
}