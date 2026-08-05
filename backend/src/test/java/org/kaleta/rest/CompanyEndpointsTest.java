package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kaleta.framework.Assert;
import org.kaleta.model.CompanyAggregates;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyUpdateDto;
import org.kaleta.rest.dto.CompanyValuesDto;

import java.sql.Date;
import java.util.List;
import java.util.Map;
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
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;

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
        assertThat(dto.getPortfolios().size(), is(Portfolio.values().length));
        assertThat(dto.getPortfolios().get(0).getKey(), is(Portfolio.PATRIA_DIP.toString()));
        assertThat(dto.getPortfolios().get(0).getName(), is(Portfolio.PATRIA_DIP.getName()));
        assertThat(dto.getPortfolios().get(0).getAbbreviation(), is("Pd"));
        assertThat(dto.getRecentCompanies().size(), is(6));
        assertThat(tickersFromCompanies(dto.getRecentCompanies()), is(List.of("CEZ", "RCH", "RR", "SELL", "XRSA", "XRSB")));
        assertThat(dto.getYears(), is(List.of("2024", "2023", "2022", "2021", "2020", "2018")));
    }

    @Test
    @Order(1)
    void getCompanyLists()
    {
        Map<String, List<CompanyWithStats>> dto = given().when()
                .get(path + "/lists")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().as(new TypeRef<>() {});

        List<CompanyWithStats> owned = dto.get("owned");
        assertThat(dto.size(), is(1));
        assertThat(owned.size(), is(6));
        assertThat(owned.get(0).getTicker(), is("CEZ"));
        assertThat(owned.get(1).getTicker(), is("RR"));
        assertThat(owned.get(owned.size() - 1).getTicker(), is("SELL"));
        for (int i = 1; i < owned.size(); i++) {
            Date previous = owned.get(i - 1).getLatestPurchaseDate();
            Date current = owned.get(i).getLatestPurchaseDate();
            assertThat(previous, is(not(nullValue())));
            assertThat(current, is(not(nullValue())));
            assertThat(previous.compareTo(current), greaterThanOrEqualTo(0));
        }
        assertThat(tickers(owned), hasItems("RCH", "XRSA", "XRSB"));
    }

    @Test
    @Order(2)
    void updateCompany()
    {
        CompanyUpdateDto dto = new CompanyUpdateDto();
        dto.setId(1842L);
        dto.setCurrency(Currency.K.toString());
        dto.setSector(Sector.SEMICONDUCTORS.toString());

        Assert.put204(path, dto);

        Company company = companyDao.get(1842L);

        assertThat(company.getTicker(), is("UPD"));
        assertThat(company.getCurrency(), is(Currency.valueOf(dto.getCurrency())));
        assertThat(company.getSector(), is(Sector.valueOf(dto.getSector())));
    }

    @Test
    @Order(2)
    void updateCompany_invalidParameters()
    {
        Long validCompanyId = 2287L;
        String validCurrency = Currency.$.toString();
        String validSector = Sector.SEMICONDUCTORS.toString();

        Assert.putValidationError(path, null, NOT_NULL);

        CompanyUpdateDto dto =  new CompanyUpdateDto();
        dto.setId(validCompanyId);
        dto.setCurrency(validCurrency);
        dto.setSector(validSector);

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

        dto.setId(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.setId(0L);
        Assert.putValidationError(path, dto, VALID_ID);
        dto.setId(4_294_967_296L);
        Assert.putValidationError(path, dto, VALID_ID);

        dto.setId(4_294_967_295L);
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

        Assert.post201(path, dto);

        Company company = companyDao.getByTicker(dto.getTicker());

        assertThat(company.getId(), is(not(nullValue())));
        assertThat(company.getCurrency(), is(Currency.valueOf(dto.getCurrency())));
        assertThat(company.getSector(), is(Sector.valueOf(dto.getSector())));
    }

    @Test
    @Order(2)
    void createCompany_invalidParameters()
    {
        String validTicker = "ICCCC";
        String validCurrency = Currency.$.toString();
        String validSector = Sector.SEMICONDUCTORS.toString();

        Assert.postValidationError(path, null, NOT_NULL);

        CompanyCreateDto dto =  new CompanyCreateDto();
        dto.setTicker(validTicker);
        dto.setCurrency(validCurrency);
        dto.setSector(validSector);

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
        Assert.getValidationError(path + "?sort=X" ,"must be any of Sort");

        Assert.getValidationError(path + "?currency=" + "X", "must be any of Currency");

        Assert.getValidationError(path + "?sector=" + "X", "must be any of Sector");
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

    private List<String> tickersFromCompanies(List<org.kaleta.model.Company> companies)
    {
        return companies.stream().map(org.kaleta.model.Company::getTicker).collect(Collectors.toList());
    }
}
