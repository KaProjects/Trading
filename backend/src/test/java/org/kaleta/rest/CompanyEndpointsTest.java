package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kaleta.framework.Assert;
import org.kaleta.model.CompanyAggregates;
import org.kaleta.model.CompanyGroups;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyUpdateDto;
import org.kaleta.rest.dto.CompanyValuesDto;

import java.sql.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_UUID;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompanyEndpointsTest
{
    String path = "/company";

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

        assertThat(dto.getCompanies().size(), is(26));
        assertThat(dto.getCompanies().get(0).getTicker(), is("ABCD"));
        assertThat(dto.getSectors().size(), is(Sector.values().length));
        assertThat(dto.getCurrencies().size(), is(Currency.values().length));
    }

    @Test
    @Order(1)
    void getCompanyLists()
    {
        CompanyGroups dto = given().when()
                .get(path + "/lists")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyGroups.class);

        assertThat(dto.getWatching().size(), is(22));
        assertThat(dto.getWatching().get(0).getTicker(), is("ABCD"));
        assertThat(dto.getWatching().get(dto.getWatching().size() - 1).getTicker(), is("ZZZ"));
        for (int i = 1; i < dto.getWatching().size(); i++) {
            assertThat(dto.getWatching().get(i - 1).getTicker().compareTo(dto.getWatching().get(i).getTicker()), lessThanOrEqualTo(0));
        }

        assertThat(dto.getDeprecated().size(), is(4));
        assertThat(dto.getDeprecated().get(0).getTicker(), is("UPD"));
        assertThat(dto.getDeprecated().get(1).getTicker(), is("XCW"));
        assertThat(dto.getDeprecated().get(2).getTicker(), is("XXX"));
        assertThat(dto.getDeprecated().get(3).getTicker(), is("YYY"));

        assertThat(dto.getOwned().size(), is(6));
        assertThat(dto.getOwned().get(0).getTicker(), is("CEZ"));
        assertThat(dto.getOwned().get(1).getTicker(), is("RR"));
        assertThat(dto.getOwned().get(dto.getOwned().size() - 1).getTicker(), is("SELL"));
        for (int i = 1; i < dto.getOwned().size(); i++) {
            Date previous = dto.getOwned().get(i - 1).getLatestPurchaseDate();
            Date current = dto.getOwned().get(i).getLatestPurchaseDate();
            assertThat(previous, is(not(nullValue())));
            assertThat(current, is(not(nullValue())));
            assertThat(previous.compareTo(current), greaterThanOrEqualTo(0));
        }
        assertThat(tickers(dto.getOwned()), hasItems("RCH", "XRSA", "XRSB"));

        assertThat(dto.getUnreported().size(), is(5));
        assertThat(dto.getUnreported().get(0).getLatestUnreportedPeriodEndingMonth().toString(), is("2025-01"));
        assertThat(dto.getUnreported().get(1).getLatestUnreportedPeriodEndingMonth().toString(), is("2025-01"));
        assertThat(dto.getUnreported().get(2).getLatestUnreportedPeriodEndingMonth().toString(), is("2025-03"));
        assertThat(tickers(dto.getUnreported()), hasItems("YYY", "UINV", "UPD", "NVDA", "RCH"));
        for (int i = 1; i < dto.getUnreported().size(); i++) {
            assertThat(dto.getUnreported().get(i - 1).getLatestUnreportedPeriodEndingMonth()
                    .compareTo(dto.getUnreported().get(i).getLatestUnreportedPeriodEndingMonth()), lessThanOrEqualTo(0));
        }

        assertThat(dto.getSectors().size(), is(3));
        assertThat(dto.getSectors().get(Sector.SEMICONDUCTORS.getName()), is(not(nullValue())));
        assertThat(dto.getSectors().get(Sector.SEMICONDUCTORS.getName()).size(), is(1));
        assertThat(dto.getSectors().get(Sector.SEMICONDUCTORS.getName()).get(0).getTicker(), is("NVDA"));
        assertThat(dto.getSectors().get(Sector.ELECTRIC_VEHICLES.getName()), is(not(nullValue())));
        assertThat(dto.getSectors().get(Sector.ELECTRIC_VEHICLES.getName()).size(), is(2));
        assertThat(tickers(dto.getSectors().get(Sector.ELECTRIC_VEHICLES.getName())), hasItems("UPD", "XCW"));
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

        Assert.putValidationError(path, null, NOT_NULL);

        CompanyUpdateDto dto =  new CompanyUpdateDto();
        dto.setId(validCompanyId);
        dto.setCurrency(validCurrency);
        dto.setSector(validSector);
        dto.setWatching(validWatching);

        dto.setCurrency(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.setCurrency("");
        Assert.putValidationError(path, dto, "must be any of Currency");
        dto.setCurrency("xyz");
        Assert.putValidationError(path, dto, "must be any of Currency");
        dto.setCurrency(validCurrency);

        dto.setSector("");
        Assert.putValidationError(path, dto, "must be any of Sector");
        dto.setSector("xyz");
        Assert.putValidationError(path, dto, "must be any of Sector");
        dto.setSector(validSector);

        dto.setWatching(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.setWatching("");
        Assert.putValidationError(path, dto, "must be 'true' or 'false'");
        dto.setWatching("xyz");
        Assert.putValidationError(path, dto, "must be 'true' or 'false'");
        dto.setWatching(validWatching);

        dto.setId(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.setId("");
        Assert.putValidationError(path, dto, VALID_UUID);
        dto.setId("x");
        Assert.putValidationError(path, dto, VALID_UUID);

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

        Assert.postValidationError(path, null, NOT_NULL);

        CompanyCreateDto dto =  new CompanyCreateDto();
        dto.setTicker(validTicker);
        dto.setCurrency(validCurrency);
        dto.setSector(validSector);
        dto.setWatching(validWatching);

        dto.setCurrency(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setCurrency("");
        Assert.postValidationError(path, dto, "must be any of Currency");
        dto.setCurrency("xyz");
        Assert.postValidationError(path, dto, "must be any of Currency");
        dto.setCurrency(validCurrency);

        dto.setSector("");
        Assert.postValidationError(path, dto, "must be any of Sector");
        dto.setSector("xyz");
        Assert.postValidationError(path, dto, "must be any of Sector");
        dto.setSector(validSector);

        dto.setWatching(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setWatching("");
        Assert.postValidationError(path, dto, "must be 'true' or 'false'");
        dto.setWatching("xyz");
        Assert.postValidationError(path, dto, "must be 'true' or 'false'");
        dto.setWatching(validWatching);

        dto.setTicker(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setTicker("");
        Assert.postValidationError(path, dto, "must be a valid Ticker");
        dto.setTicker("ABCDEF");
        Assert.postValidationError(path, dto, "must be a valid Ticker");
        dto.setTicker("abcd");
        Assert.postValidationError(path, dto, "must be a valid Ticker");
        dto.setTicker("NVDA");
        Assert.post400(path, dto, "company with ticker '" + dto.getTicker() + "' already exists!");
    }

    @Test
    @Order(1)
    void getCompaniesWithAggregates()
    {
        CompanyAggregates dto = given().when()
                .get(path)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(26));
        CompanyAggregates.Company company = dto.getCompanies().get(7);
        assertThat(company.getTicker(), is("NVDA"));
        assertThat(company.getCurrency(), is(Currency.$));
        assertThat(company.getWatching(), is(true));
        assertThat(company.getSector().getKey(), is(Sector.SEMICONDUCTORS.toString()));
        assertThat(company.getTotalTrades(), is(1));
        assertThat(company.getActiveTrades(), is(0));
        assertThat(company.getDividends(), is(2));
        assertThat(company.getRecords(), is(2));
        assertThat(company.getPeriods(), is(3));
    }

    @Test
    @Order(1)
    void getCompaniesWithAggregates_invalidParameters()
    {
        Assert.getValidationError(path + "?sort=" ,"must be any of Sort");
        Assert.getValidationError(path + "?sort=X" ,"must be any of Sort");

        Assert.getValidationError(path + "?currency=" + "X", "must be any of Currency");
        Assert.getValidationError(path + "?currency=", "must be any of Currency");

        Assert.getValidationError(path + "?sector=" + "X", "must be any of Sector");
        Assert.getValidationError(path + "?sector=", "must be any of Sector");
    }

    @Test
    @Order(1)
    void getCompaniesWithAggregates_filterCurrency()
    {
        CompanyAggregates dto = given().when()
                .get(path + "?currency=" + Currency.€)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(1));
        assertThat(dto.getCompanies().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getCompanies().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getCompanies().get(0).getWatching(), is(true));
        assertThat(dto.getCompanies().get(0).getSector().getKey(), is(Sector.ENERGY_MINERALS.toString()));
        assertThat(dto.getCompanies().get(0).getTotalTrades(), is(1));
        assertThat(dto.getCompanies().get(0).getActiveTrades(), is(0));
        assertThat(dto.getCompanies().get(0).getDividends(), is(0));
        assertThat(dto.getCompanies().get(0).getRecords(), is(2));
        assertThat(dto.getCompanies().get(0).getPeriods(), is(0));
    }

    @Test
    @Order(1)
    void getCompaniesWithAggregates_filterSector()
    {
        CompanyAggregates dto = given().when()
                .get(path + "?sector=" + Sector.ENERGY_MINERALS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(1));
        assertThat(dto.getCompanies().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getCompanies().get(0).getCurrency(), is(Currency.€));
        assertThat(dto.getCompanies().get(0).getWatching(), is(true));
        assertThat(dto.getCompanies().get(0).getSector().getKey(), is(Sector.ENERGY_MINERALS.toString()));
        assertThat(dto.getCompanies().get(0).getTotalTrades(), is(1));
        assertThat(dto.getCompanies().get(0).getActiveTrades(), is(0));
        assertThat(dto.getCompanies().get(0).getDividends(), is(0));
        assertThat(dto.getCompanies().get(0).getRecords(), is(2));
        assertThat(dto.getCompanies().get(0).getPeriods(), is(0));
    }

    @Test
    @Order(1)
    void getCompaniesWithAggregates_sorts()
    {
        CompanyAggregates dto = given().when()
                .get(path + "?sort=" + CompanyAggregates.Sort.TICKER)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        int expectedCompanies = 26;

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        CompanyAggregates.Company company = dto.getCompanies().get(7);
        assertThat(company.getTicker(), is("NVDA"));
        assertThat(company.getCurrency(), is(Currency.$));
        assertThat(company.getWatching(), is(true));
        assertThat(company.getSector().getKey(), is(Sector.SEMICONDUCTORS.toString()));
        assertThat(company.getTotalTrades(), is(1));
        assertThat(company.getActiveTrades(), is(0));
        assertThat(company.getDividends(), is(2));
        assertThat(company.getRecords(), is(2));
        assertThat(company.getPeriods(), is(3));

        dto = given().when()
                .get(path + "?sort=" + CompanyAggregates.Sort.CURRENCY)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getCurrency().compareTo(dto.getCompanies().get(i).getCurrency()), lessThanOrEqualTo(0));
        }

        dto = given().when()
                .get(path + "?sort=" + CompanyAggregates.Sort.WATCHING)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getWatching().compareTo(dto.getCompanies().get(i).getWatching()), greaterThanOrEqualTo(0));
        }

        dto = given().when()
                .get(path + "?sort=" + CompanyAggregates.Sort.SECTOR)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
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
                .get(path + "?sort=" + CompanyAggregates.Sort.ALL_TRADES)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getTotalTrades(), greaterThanOrEqualTo(dto.getCompanies().get(i).getTotalTrades()));
        }

        dto = given().when()
                .get(path + "?sort=" + CompanyAggregates.Sort.ACTIVE_TRADES)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getActiveTrades(), greaterThanOrEqualTo(dto.getCompanies().get(i).getActiveTrades()));
        }

        dto = given().when()
                .get(path + "?sort=" + CompanyAggregates.Sort.DIVIDENDS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getDividends(), greaterThanOrEqualTo(dto.getCompanies().get(i).getDividends()));
        }

        dto = given().when()
                .get(path + "?sort=" + CompanyAggregates.Sort.RECORDS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getRecords(), greaterThanOrEqualTo(dto.getCompanies().get(i).getRecords()));
        }

        dto = given().when()
                .get(path + "?sort=" + CompanyAggregates.Sort.PERIODS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyAggregates.class);

        assertThat(dto.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(dto.getCompanies().size(), is(expectedCompanies));
        for (int i=1; i<dto.getCompanies().size(); i++){
            assertThat(dto.getCompanies().get(i-1).getPeriods(), greaterThanOrEqualTo(dto.getCompanies().get(i).getPeriods()));
        }
    }

    private List<String> tickers(List<CompanyWithStats> companies)
    {
        return companies.stream().map(CompanyWithStats::getTicker).collect(Collectors.toList());
    }
}
