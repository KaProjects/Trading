package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.CompanyDto;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@QuarkusTest
class CompanyResourceTest
{
    @Test
    void getCompanies()
    {
        List<CompanyDto> dtos = given().when()
                .get("/company")
                .then()
                .statusCode(200)
                .body("size()", is(8))
                .extract().response().jsonPath().getList("", CompanyDto.class);

        assertThat(dtos.get(0).getTicker(), is("ABCD"));
    }
}