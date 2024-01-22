package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.CompanyDto;
import org.kaleta.dto.CompanyListsDto;

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
                .body("size()", is(10))
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

        System.out.println(dto);

        assertThat(dto.getWatchingOldestReview().size(), is(8));
        assertThat(dto.getWatchingOldestReview().get(0).getTicker(), is("XRC"));
        assertThat(dto.getWatchingOldestReview().get(1).getTicker(), is("XRSB"));

        assertThat(dto.getOwnedWithoutStrategy().size(), is(3));
        assertThat(dto.getOwnedWithoutStrategy().get(0).getTicker(), is("XRSB"));

        assertThat(dto.getNotWatching().size(), is(2));
        assertThat(dto.getNotWatching().get(0).getTicker(), is("XXX"));
    }
}