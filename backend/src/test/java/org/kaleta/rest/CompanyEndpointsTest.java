package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kaleta.dto.RecordsUiCompanyListsDto;
import org.kaleta.framework.Assert;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.persistence.entity.Sort;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyUiDto;
import org.kaleta.rest.dto.CompanyUpdateDto;
import org.kaleta.rest.dto.CompanyValuesDto;

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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompanyEndpointsTest
{
    private final String path = "/company";

    @Inject
    CompanyDao companyDao;

    @Test
    @Order(1)
    void getCompanyValues()
    {
        CompanyValuesDto dto = given().when()
                .get(path + "/values")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyValuesDto.class);

        assertThat(dto.getCompanies().size(), is(25));
        assertThat(dto.getCompanies().get(0).getTicker(), is("ABCD"));
        assertThat(dto.getSectors().size(), is(Sector.values().length));
        assertThat(dto.getCurrencies().size(), is(Currency.values().length));
    }

    @Test
    @Order(1)
    void getCompanyLists()
    {
        RecordsUiCompanyListsDto dto = given().when()
                .get(path + "/lists")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiCompanyListsDto.class);

        assertThat(dto.getWatchingOldestReview().size(), is(21));
        assertThat(dto.getWatchingOldestReview().get(15).getTicker(), is("RR"));

        assertThat(dto.getOwnedWithoutStrategy().size(), is(5));
        assertThat(dto.getOwnedWithoutStrategy(), hasItem(hasTicker("XRSB")));
        assertThat(dto.getOwnedWithoutStrategy(), not(hasItem(hasTicker("XRSA"))));

        assertThat(dto.getNotWatching().size(), is(4));
        assertThat(dto.getNotWatching(), hasItem(hasTicker("XCW")));
        assertThat(dto.getNotWatching(), hasItem(hasTicker("XXX")));
        assertThat(dto.getNotWatching(), hasItem(hasTicker("YYY")));

        assertThat(dto.getSectors().size(), is(3));
        assertThat(dto.getSectors().get(Sector.SEMICONDUCTORS.getName()), is(not(nullValue())));
        assertThat(dto.getSectors().get(Sector.SEMICONDUCTORS.getName()).size(), is(1));
        assertThat(dto.getSectors().get(Sector.SEMICONDUCTORS.getName()).get(0).getTicker(), is("NVDA"));
        assertThat(dto.getSectors().get(Sector.ELECTRIC_VEHICLES.getName()), is(not(nullValue())));
        assertThat(dto.getSectors().get(Sector.ELECTRIC_VEHICLES.getName()).size(), is(2));
        assertThat(dto.getSectors().get(Sector.ELECTRIC_VEHICLES.getName()).get(0).getTicker(), is("XCW"));
        assertThat(dto.getSectors().get(Sector.ENERGY_MINERALS.getName()), is(not(nullValue())));
        assertThat(dto.getSectors().get(Sector.ENERGY_MINERALS.getName()).size(), is(1));
        assertThat(dto.getSectors().get(Sector.ENERGY_MINERALS.getName()).get(0).getTicker(), is("SHELL"));
    }

    @Test
    @Order(2)
    void updateCompany()
    {
        CompanyUpdateDto dto = new CompanyUpdateDto();
        dto.setId("9c858901-8a57-4791-81fe-4c455b099bc9");
        dto.setCurrency(Currency.K.toString());
        dto.setSector(Sector.SEMICONDUCTORS.toString());
        dto.setWatching(Boolean.FALSE.toString());

        Assert.put204(path, dto);

        Company company = companyDao.get("9c858901-8a57-4791-81fe-4c455b099bc9");

        assertThat(company.getTicker(), is("UPD"));
        assertThat(company.getCurrency(), is(Currency.valueOf(dto.getCurrency())));
        assertThat(company.getSector(), is(Sector.valueOf(dto.getSector())));
        assertThat(company.isWatching(), is(Boolean.parseBoolean(dto.getWatching())));
    }

    @Test
    @Order(2)
    void updateCompany_invalidParameters()
    {
        String validCompanyId = "f5b87b39-6b61-4c32-8c09-4f34e97c2d7d";
        String validCurrency = Currency.$.toString();
        String validSector = Sector.SEMICONDUCTORS.toString();
        String validWatching = "false";

        Assert.putValidationError(path, null, "must not be null");

        CompanyUpdateDto dto =  new CompanyUpdateDto();
        dto.setId(validCompanyId);
        dto.setCurrency(validCurrency);
        dto.setSector(validSector);
        dto.setWatching(validWatching);

        dto.setCurrency(null);
        Assert.putValidationError(path, dto, "must not be null");
        dto.setCurrency("");
        Assert.putValidationError(path, dto, "must be any of Currency");
        dto.setCurrency("xyz");
        Assert.putValidationError(path, dto, "must be any of Currency");
        dto.setCurrency(validCurrency);

        dto.setSector(null);
        Assert.putValidationError(path, dto, "must not be null");
        dto.setSector("");
        Assert.putValidationError(path, dto, "must be any of Sector");
        dto.setSector("xyz");
        Assert.putValidationError(path, dto, "must be any of Sector");
        dto.setSector(validSector);

        dto.setWatching(null);
        Assert.putValidationError(path, dto, "must not be null");
        dto.setWatching("");
        Assert.putValidationError(path, dto, "must be 'true' or 'false'");
        dto.setWatching("xyz");
        Assert.putValidationError(path, dto, "must be 'true' or 'false'");
        dto.setWatching(validWatching);

        dto.setId(null);
        Assert.putValidationError(path, dto, "must not be null");
        dto.setId("");
        Assert.putValidationError(path, dto, "must be a valid UUID");
        dto.setId("x");
        Assert.putValidationError(path, dto, "must be a valid UUID");

        dto.setId(UUID.randomUUID().toString());
        Assert.put400(path, dto, "company with id '" + dto.getId() + "' not found");
    }

    @Test
    @Order(2)
    void createCompany()
    {
        CompanyCreateDto dto = new CompanyCreateDto();
        dto.setTicker("CCCCC");
        dto.setCurrency(Currency.K.toString());
        dto.setSector(Sector.SEMICONDUCTORS.toString());
        dto.setWatching(Boolean.FALSE.toString());

        Assert.post201(path, dto);

        Company company = companyDao.getByTicker(dto.getTicker());

        assertThat(company.getId(), is(not(nullValue())));
        assertThat(company.getCurrency(), is(Currency.valueOf(dto.getCurrency())));
        assertThat(company.getSector(), is(Sector.valueOf(dto.getSector())));
        assertThat(company.isWatching(), is(Boolean.parseBoolean(dto.getWatching())));
    }

    @Test
    @Order(2)
    void createCompany_invalidParameters()
    {
        String validTicker = "ICCCC";
        String validCurrency = Currency.$.toString();
        String validSector = Sector.SEMICONDUCTORS.toString();
        String validWatching = "false";

        Assert.postValidationError(path, null, "must not be null");

        CompanyCreateDto dto =  new CompanyCreateDto();
        dto.setTicker(validTicker);
        dto.setCurrency(validCurrency);
        dto.setSector(validSector);
        dto.setWatching(validWatching);

        dto.setCurrency(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setCurrency("");
        Assert.postValidationError(path, dto, "must be any of Currency");
        dto.setCurrency("xyz");
        Assert.postValidationError(path, dto, "must be any of Currency");
        dto.setCurrency(validCurrency);

        dto.setSector(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setSector("");
        Assert.postValidationError(path, dto, "must be any of Sector");
        dto.setSector("xyz");
        Assert.postValidationError(path, dto, "must be any of Sector");
        dto.setSector(validSector);

        dto.setWatching(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setWatching("");
        Assert.postValidationError(path, dto, "must be 'true' or 'false'");
        dto.setWatching("xyz");
        Assert.postValidationError(path, dto, "must be 'true' or 'false'");
        dto.setWatching(validWatching);

        dto.setTicker(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setTicker("");
        Assert.postValidationError(path, dto, "must be a valid Ticker");
        dto.setTicker("ABCDEF");
        Assert.postValidationError(path, dto, "must be a valid Ticker");
        dto.setTicker("abcd");
        Assert.postValidationError(path, dto, "must be a valid Ticker");
    }

    @Test
    @Order(1)
    void getCompaniesWithAggregates()
    {
        CompanyUiDto dto = given().when()
                .get(path)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(25));
        CompanyUiDto.Company company = dto.getCompanies().get(6);
        assertThat(company.getTicker(), is("NVDA"));
        assertThat(company.getCurrency(), is(Currency.$));
        assertThat(company.getWatching(), is(true));
        assertThat(company.getSector().getKey(), is(Sector.SEMICONDUCTORS.toString()));
        assertThat(company.getTotalTrades(), is(1));
        assertThat(company.getActiveTrades(), is(0));
        assertThat(company.getDividends(), is(2));
        assertThat(company.getRecords(), is(2));
        assertThat(company.getFinancials(), is(2));
    }

    @Test
    @Order(1)
    void getCompaniesWithAggregates_invalidParameters()
    {
        Assert.getValidationError(path + "?sort=" ,"must be any of CompanyAggregate");
        Assert.getValidationError(path + "?sort=X" ,"must be any of CompanyAggregate");

        Assert.getValidationError(path + "?currency=" + "X", "must be any of Currency");
        Assert.getValidationError(path + "?currency=", "must be any of Currency");

        Assert.getValidationError(path + "?sector=" + "X", "must be any of Sector");
        Assert.getValidationError(path + "?sector=", "must be any of Sector");
    }

    @Test
    @Order(1)
    void getCompaniesWithAggregates_filterCurrency()
    {
        CompanyUiDto dto = given().when()
                .get(path + "?currency=" + Currency.€)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(1));
        assertThat(dto.getCompanies().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getCompanies().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getCompanies().get(0).getWatching(), is(true));
        assertThat(dto.getCompanies().get(0).getSector().getKey(), is(Sector.ENERGY_MINERALS.toString()));
        assertThat(dto.getCompanies().get(0).getTotalTrades(), is(1));
        assertThat(dto.getCompanies().get(0).getActiveTrades(), is(0));
        assertThat(dto.getCompanies().get(0).getDividends(), is(0));
        assertThat(dto.getCompanies().get(0).getRecords(), is(2));
        assertThat(dto.getCompanies().get(0).getFinancials(), is(0));
    }

    @Test
    @Order(1)
    void getCompaniesWithAggregates_filterSector()
    {
        CompanyUiDto dto = given().when()
                .get(path + "?sector=" + Sector.ENERGY_MINERALS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(9));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(1));
        assertThat(dto.getCompanies().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getCompanies().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getCompanies().get(0).getWatching(), is(true));
        assertThat(dto.getCompanies().get(0).getSector().getKey(), is(Sector.ENERGY_MINERALS.toString()));
        assertThat(dto.getCompanies().get(0).getTotalTrades(), is(1));
        assertThat(dto.getCompanies().get(0).getActiveTrades(), is(0));
        assertThat(dto.getCompanies().get(0).getDividends(), is(0));
        assertThat(dto.getCompanies().get(0).getRecords(), is(2));
        assertThat(dto.getCompanies().get(0).getFinancials(), is(0));
    }

    @Test
    @Order(1)
    void getCompaniesWithAggregates_sorts()
    {
        CompanyUiDto dto = given().when()
                .get(path + "?sort=" + Sort.CompanyAggregate.COMPANY)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        int expectedColumns = 9;
        int expectedCompanies = 25;

        assertThat(dto.getColumns().size(), is(expectedColumns));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        CompanyUiDto.Company company = dto.getCompanies().get(6);
        assertThat(company.getTicker(), is("NVDA"));
        assertThat(company.getCurrency(), is(Currency.$));
        assertThat(company.getWatching(), is(true));
        assertThat(company.getSector().getKey(), is(Sector.SEMICONDUCTORS.toString()));
        assertThat(company.getTotalTrades(), is(1));
        assertThat(company.getActiveTrades(), is(0));
        assertThat(company.getDividends(), is(2));
        assertThat(company.getRecords(), is(2));
        assertThat(company.getFinancials(), is(2));

        dto = given().when()
                .get(path + "?sort=" + Sort.CompanyAggregate.CURRENCY)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(expectedColumns));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getCurrency().compareTo(dto.getCompanies().get(i).getCurrency()), lessThanOrEqualTo(0));
        }

        dto = given().when()
                .get(path + "?sort=" + Sort.CompanyAggregate.WATCHING)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(expectedColumns));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getWatching().compareTo(dto.getCompanies().get(i).getWatching()), greaterThanOrEqualTo(0));
        }

        dto = given().when()
                .get(path + "?sort=" + Sort.CompanyAggregate.SECTOR)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(expectedColumns));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        assertThat(dto.getCompanies().get(0).getSector(), is(not(nullValue())));
        for (int i=0; i<dto.getCompanies().size() - 1; i++){
            if (dto.getCompanies().get(i).getSector() != null){
                assertThat(dto.getCompanies().get(i).getSector().compareTo(dto.getCompanies().get(i + 1).getSector()), lessThanOrEqualTo(0));
            } else {
                assertThat(dto.getCompanies().get(i + 1).getSector(), is(nullValue()));
            }
        }

        dto = given().when()
                .get(path + "?sort=" + Sort.CompanyAggregate.ALL_TRADES)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(expectedColumns));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getTotalTrades(), greaterThanOrEqualTo(dto.getCompanies().get(i).getTotalTrades()));
        }

        dto = given().when()
                .get(path + "?sort=" + Sort.CompanyAggregate.ACTIVE_TRADES)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(expectedColumns));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getActiveTrades(), greaterThanOrEqualTo(dto.getCompanies().get(i).getActiveTrades()));
        }

        dto = given().when()
                .get(path + "?sort=" + Sort.CompanyAggregate.DIVIDENDS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(expectedColumns));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getDividends(), greaterThanOrEqualTo(dto.getCompanies().get(i).getDividends()));
        }

        dto = given().when()
                .get(path + "?sort=" + Sort.CompanyAggregate.RECORDS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(expectedColumns));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getRecords(), greaterThanOrEqualTo(dto.getCompanies().get(i).getRecords()));
        }

        dto = given().when()
                .get(path + "?sort=" + Sort.CompanyAggregate.FINANCIALS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyUiDto.class);

        assertThat(dto.getColumns().size(), is(expectedColumns));
        assertThat(dto.getSorts().size(), is(Sort.CompanyAggregate.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getFinancials(), greaterThanOrEqualTo(dto.getCompanies().get(i).getFinancials()));
        }
    }
}