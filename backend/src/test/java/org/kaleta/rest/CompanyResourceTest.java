package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.CompanyDto;
import org.kaleta.dto.CompanyListsDto;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
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
                .body("size()", is(11))
                .extract().response().jsonPath().getList("", CompanyDto.class);

        assertThat(dtos.get(0).getTicker(), is("ABCD"));
    }

    @Test
    void getCompanyLists()
    {
        CompanyListsDto dto = given().when()
                .get("/company/lists")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyListsDto.class);

        assertThat(dto.getWatchingOldestReview().size(), is(8));
        assertThat(dto.getWatchingOldestReview().get(0).getTicker(), is("XRC"));
        assertThat(dto.getWatchingOldestReview().get(1).getTicker(), is("XRSB"));

        assertThat(dto.getOwnedWithoutStrategy().size(), is(3));
        assertThat(dto.getOwnedWithoutStrategy().get(0).getTicker(), is("XRSB"));

        assertThat(dto.getNotWatching().size(), is(3));
        assertThat(dto.getNotWatching().get(0).getTicker(), is("XCW"));
    }

    @Test
    void updateCompany()
    {
        CompanyDto dto = new CompanyDto();
        dto.setId("5afe260b-c433-426c-9710-e9ff99faa5aa");
        dto.setWatching(true);

        given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/company")
                .then().statusCode(Response.Status.NO_CONTENT.getStatusCode());

        List<CompanyDto> companies = given().when()
                .get("/company")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getList("", CompanyDto.class);

        boolean companyFound = false;
        for (CompanyDto dtoAfter : companies) {
            if (dtoAfter.getId().equals(dto.getId())) {
                assertThat(dtoAfter.getTicker(), is("XCW"));
                assertThat(dtoAfter.getCurrency(), is("$"));
                assertThat(dtoAfter.getWatching(), is(true));
                companyFound = true;
            }
        }
        assertThat(companyFound, is(true));
    }

    @Test
    void parameterValidator()
    {
        assertThat(given().when()
                .put("/company")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Payload is NULL"));

        CompanyDto dto =  new CompanyDto();
        assertThat(given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .put("/company")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), Matchers.is("Invalid UUID Parameter: 'null'"));

        dto.setId(UUID.randomUUID().toString());
        assertThat(given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .put("/company")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), Matchers.is("company with id '" + dto.getId() + "' not found"));
    }
}