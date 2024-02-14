package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.CompanyDto;
import org.kaleta.dto.CompanyUiDto;
import org.kaleta.dto.RecordsUiCompanyListsDto;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Sector;
import org.kaleta.entity.Sort;
import org.kaleta.framework.Assert;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.kaleta.framework.Matchers.hasTicker;

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
                .body("size()", is(15))
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

        assertThat(dto.getWatchingOldestReview().size(), is(12));
        assertThat(dto.getWatchingOldestReview().get(11).getTicker(), is("NVDA"));

        assertThat(dto.getOwnedWithoutStrategy().size(), is(4));
        assertThat(dto.getOwnedWithoutStrategy(), hasItem(hasTicker("XRSB")));
        assertThat(dto.getOwnedWithoutStrategy(), not(hasItem(hasTicker("XRSA"))));

        assertThat(dto.getNotWatching().size(), is(3));
        assertThat(dto.getNotWatching(), hasItem(hasTicker("XCW")));
        assertThat(dto.getNotWatching(), hasItem(hasTicker("XXX")));
        assertThat(dto.getNotWatching(), hasItem(hasTicker("YYY")));

        assertThat(dto.getSectors().keySet().size(), is(3));
        assertThat(dto.getSectors().get(Sector.SEMICONDUCTORS.getName()), is(not(nullValue())));
        assertThat(dto.getSectors().get(Sector.SEMICONDUCTORS.getName()).size(), is(1));
        assertThat(dto.getSectors().get(Sector.SEMICONDUCTORS.getName()).get(0).getTicker(), is("NVDA"));
        assertThat(dto.getSectors().get(Sector.ELECTRIC_VEHICLES.getName()), is(not(nullValue())));
        assertThat(dto.getSectors().get(Sector.ELECTRIC_VEHICLES.getName()).size(), is(1));
        assertThat(dto.getSectors().get(Sector.ELECTRIC_VEHICLES.getName()).get(0).getTicker(), is("XCW"));
        assertThat(dto.getSectors().get(Sector.ENERGY_MINERALS.getName()), is(not(nullValue())));
        assertThat(dto.getSectors().get(Sector.ENERGY_MINERALS.getName()).size(), is(1));
        assertThat(dto.getSectors().get(Sector.ENERGY_MINERALS.getName()).get(0).getTicker(), is("SHELL"));
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
                assertThat(dtoAfter.getCurrency(), is(Currency.$));
                assertThat(dtoAfter.getSector(), is(Sector.ELECTRIC_VEHICLES.getName()));
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

        Assert.get400("/company/aggregate?sort=" ,"Invalid Company Aggregate Sort Parameter:");
        Assert.get400("/company/aggregate?sort=X" ,"Invalid Company Aggregate Sort Parameter:");

        Assert.get400("/company/aggregate?currency=" + "X", "Invalid Currency Parameter");
        Assert.get400("/company/aggregate?currency=", "Invalid Currency Parameter");

        Assert.get400("/company/aggregate?sector=" + "X", "Invalid Sector Parameter");
        Assert.get400("/company/aggregate?sector=", "Invalid Sector Parameter");
    }

    @Test
    void getCompaniesWithAggregates()
    {
        CompanyUiDto dto = given().when()
                .get("/company/aggregate")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(15));
        assertThat(dto.getCompanies().get(2).getTicker(), is("NVDA"));
        assertThat(dto.getCompanies().get(2).getCurrency(), is(Currency.$));
        assertThat(dto.getCompanies().get(2).getWatching(), is(true));
        assertThat(dto.getCompanies().get(2).getSector(), is(Sector.SEMICONDUCTORS.getName()));
        assertThat(dto.getCompanies().get(2).getTotalTrades(), is(1));
        assertThat(dto.getCompanies().get(2).getActiveTrades(), is(0));
        assertThat(dto.getCompanies().get(2).getDividends(), is(2));
        assertThat(dto.getCompanies().get(2).getRecords(), is(2));
        assertThat(dto.getCompanies().get(2).getFinancials(), is(3));
    }

    @Test
    void getCompaniesWithAggregatesFilterCurrency()
    {
        CompanyUiDto dto = given().when()
                .get("/company/aggregate?currency=" + Currency.€)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(1));
        assertThat(dto.getCompanies().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getCompanies().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getCompanies().get(0).getWatching(), is(true));
        assertThat(dto.getCompanies().get(0).getSector(), is(Sector.ENERGY_MINERALS.getName()));
        assertThat(dto.getCompanies().get(0).getTotalTrades(), is(1));
        assertThat(dto.getCompanies().get(0).getActiveTrades(), is(0));
        assertThat(dto.getCompanies().get(0).getDividends(), is(0));
        assertThat(dto.getCompanies().get(0).getRecords(), is(2));
        assertThat(dto.getCompanies().get(0).getFinancials(), is(0));
    }

    @Test
    void getCompaniesWithAggregatesFilterSector()
    {
        CompanyUiDto dto = given().when()
                .get("/company/aggregate?sector=" + Sector.ENERGY_MINERALS.getName())
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(1));
        assertThat(dto.getCompanies().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getCompanies().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getCompanies().get(0).getWatching(), is(true));
        assertThat(dto.getCompanies().get(0).getSector(), is(Sector.ENERGY_MINERALS.getName()));
        assertThat(dto.getCompanies().get(0).getTotalTrades(), is(1));
        assertThat(dto.getCompanies().get(0).getActiveTrades(), is(0));
        assertThat(dto.getCompanies().get(0).getDividends(), is(0));
        assertThat(dto.getCompanies().get(0).getRecords(), is(2));
        assertThat(dto.getCompanies().get(0).getFinancials(), is(0));
    }

    @Test
    void getCompaniesWithAggregatesSorts()
    {
        CompanyUiDto dto = given().when()
                .get("/company/aggregate?sort=" + Sort.CompanyAggregate.COMPANY)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(15));
        assertThat(dto.getCompanies().get(2).getTicker(), is("NVDA"));
        assertThat(dto.getCompanies().get(2).getCurrency(), is(Currency.$));
        assertThat(dto.getCompanies().get(2).getWatching(), is(true));
        assertThat(dto.getCompanies().get(2).getSector(), is(Sector.SEMICONDUCTORS.getName()));
        assertThat(dto.getCompanies().get(2).getTotalTrades(), is(1));
        assertThat(dto.getCompanies().get(2).getActiveTrades(), is(0));
        assertThat(dto.getCompanies().get(2).getDividends(), is(2));
        assertThat(dto.getCompanies().get(2).getRecords(), is(2));
        assertThat(dto.getCompanies().get(2).getFinancials(), is(3));

        dto = given().when()
                .get("/company/aggregate?sort=" + Sort.CompanyAggregate.CURRENCY)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(15));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getCurrency().compareTo(dto.getCompanies().get(i).getCurrency()), lessThanOrEqualTo(0));
        }

        dto = given().when()
                .get("/company/aggregate?sort=" + Sort.CompanyAggregate.WATCHING)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(15));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getWatching().compareTo(dto.getCompanies().get(i).getWatching()), greaterThanOrEqualTo(0));
        }

        dto = given().when()
                .get("/company/aggregate?sort=" + Sort.CompanyAggregate.SECTOR)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(15));
        assertThat(dto.getCompanies().get(0).getSector(), is(not(nullValue())));
        for (int i=0; i<dto.getCompanies().size() - 1; i++){
            String sectorI = dto.getCompanies().get(i).getSector();
            String sectorI1 = dto.getCompanies().get(i + 1).getSector();
            if (sectorI != null && sectorI1 != null){
                assertThat(sectorI.compareTo(sectorI1), lessThanOrEqualTo(0));
            }
        }

        dto = given().when()
                .get("/company/aggregate?sort=" + Sort.CompanyAggregate.ALL_TRADES)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(15));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getTotalTrades(), greaterThanOrEqualTo(dto.getCompanies().get(i).getTotalTrades()));
        }

        dto = given().when()
                .get("/company/aggregate?sort=" + Sort.CompanyAggregate.ACTIVE_TRADES)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(15));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getActiveTrades(), greaterThanOrEqualTo(dto.getCompanies().get(i).getActiveTrades()));
        }

        dto = given().when()
                .get("/company/aggregate?sort=" + Sort.CompanyAggregate.DIVIDENDS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(15));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getDividends(), greaterThanOrEqualTo(dto.getCompanies().get(i).getDividends()));
        }

        dto = given().when()
                .get("/company/aggregate?sort=" + Sort.CompanyAggregate.RECORDS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(15));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getRecords(), greaterThanOrEqualTo(dto.getCompanies().get(i).getRecords()));
        }

        dto = given().when()
                .get("/company/aggregate?sort=" + Sort.CompanyAggregate.FINANCIALS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getCompanies().size(), is(15));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getFinancials(), greaterThanOrEqualTo(dto.getCompanies().get(i).getFinancials()));
        }
    }
}