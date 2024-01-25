package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.CompanyDto;
import org.kaleta.dto.RecordsUiCompanyListsDto;
import org.kaleta.framework.Assert;

import java.util.List;
import java.util.UUID;

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
                .body("size()", is(13))
                .extract().response().jsonPath().getList("", CompanyDto.class);

        assertThat(dtos.get(0).getTicker(), is("ABCD"));
    }

    @Test
    void getCompanyLists()
    {
        RecordsUiCompanyListsDto dto = given().when()
                .get("/company/lists")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiCompanyListsDto.class);

        assertThat(dto.getWatchingOldestReview().size(), is(10));
        assertThat(dto.getWatchingOldestReview().get(0).getTicker(), is("XRC"));

        assertThat(dto.getOwnedWithoutStrategy().size(), is(4));
        assertThat(dto.getOwnedWithoutStrategy().get(0).getTicker(), is("XTS"));

        assertThat(dto.getNotWatching().size(), is(3));
        assertThat(dto.getNotWatching().get(0).getTicker(), is("XCW"));
    }

    @Test
    void updateCompany()
    {
        CompanyDto dto = new CompanyDto();
        dto.setId("5afe260b-c433-426c-9710-e9ff99faa5aa");
        dto.setWatching(true);

        Assert.put204("/company", dto);

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
        Assert.put400("/company", null, "Payload is NULL");

        CompanyDto dto =  new CompanyDto();
        Assert.put400("/company", dto, "Invalid UUID Parameter: 'null'");

        dto.setId(UUID.randomUUID().toString());
        Assert.put400("/company", dto, "company with id '" + dto.getId() + "' not found");

    }
}