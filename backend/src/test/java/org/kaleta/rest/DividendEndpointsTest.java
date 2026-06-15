package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kaleta.framework.Assert;
import org.kaleta.model.Dividends;
import org.kaleta.persistence.api.DividendDao;
import org.kaleta.persistence.entity.Dividend;
import org.kaleta.rest.dto.DividendCreateDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_4_2_false;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_5_2_false;
import static org.kaleta.framework.Assert.ExpectedViolation.MATCH_DATE_FORMAT;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_UUID;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DividendEndpointsTest
{
    private static final String PATH = "/dividend";

    @Inject
    DividendDao dividendDao;

    @Test
    void getDividends()
    {
        Dividends dto = given().when()
                .get(PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Dividends.class);

        assertThat(dto.getDividends().size(), is(4));
        assertThat(dto.getDividends().get(0).getDate().toString(), is("2022-12-01"));
        assertThat(dto.getDividends().get(0).getCompany().getTicker(), is("NVDA"));
        assertThat(dto.getDividends().get(1).getDate().toString(), is("2021-12-01"));
        assertThat(dto.getDividends().get(1).getCompany().getTicker(), is("CEZ"));
        assertThat(dto.getDividends().get(2).getDate().toString(), is("2021-06-01"));
        assertThat(dto.getDividends().get(3).getDate().toString(), is("2020-12-01"));

        assertThat(dto.getAggregates().getCompanies(), is(3));
        assertThat(dto.getAggregates().getCurrencies(), is(2));
        assertBigDecimals(dto.getAggregates().getDividendSum(), new BigDecimal("2150"));
        assertBigDecimals(dto.getAggregates().getTaxSum(), new BigDecimal("215"));
        assertBigDecimals(dto.getAggregates().getNetSum(), new BigDecimal("1935"));
    }

    @Test
    void getDividends_invalidParameters()
    {
        Assert.getValidationError(PATH + "?companyId=AAAAAA", VALID_UUID);
        Assert.getValidationError(PATH + "?companyId=", VALID_UUID);

        Assert.getValidationError(PATH + "?currency=X", "must be any of Currency");
        Assert.getValidationError(PATH + "?currency=", "must be any of Currency");

        Assert.getValidationError(PATH + "?year=20x2", "must match YYYY");
        Assert.getValidationError(PATH + "?year=20222", "must match YYYY");
        Assert.getValidationError(PATH + "?year=202", "must match YYYY");
        Assert.getValidationError(PATH + "?year=", "must match YYYY");

        Assert.getValidationError(PATH + "?sector=X", "must be any of Sector");
        Assert.getValidationError(PATH + "?sector=", "must be any of Sector");
    }

    @Test
    void createDividend()
    {
        DividendCreateDto dto = new DividendCreateDto();
        dto.setCompanyId("6877c555-1234-4af5-99ef-415980484d8c");
        dto.setDate("2020-01-01");
        dto.setDividend("100.50");
        dto.setTax("10.50");

        Assert.post201(PATH, dto);

        List<Dividend> dividends = dividendDao.list(dto.getCompanyId(), null, null, null);

        assertThat(dividends.size(), is(1));

        Dividend dividend = dividends.get(0);
        assertThat(dividend.getId(), is(notNullValue()));
        assertThat(dividend.getCompany().getTicker(), is("CRE"));
        assertThat(dividend.getDate().toString(), is(dto.getDate()));
        assertBigDecimals(dividend.getDividend(), new BigDecimal(dto.getDividend()));
        assertBigDecimals(dividend.getTax(), new BigDecimal(dto.getTax()));
    }

    @Test
    void createDividend_invalidParameters()
    {
        String validCompanyId = "f5b87b39-6b61-4c32-8c09-4f34e97c2d7d";
        String validDate = "2020-01-01";
        String validDividend = "100.50";
        String validTax = "10.50";

        Assert.postValidationError(PATH, null, NOT_NULL);

        DividendCreateDto dto = new DividendCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setDate(validDate);
        dto.setDividend(validDividend);
        dto.setTax(validTax);

        dto.setCompanyId(null);
        Assert.postValidationError(PATH, dto, NOT_NULL);
        dto.setCompanyId("x");
        Assert.postValidationError(PATH, dto, VALID_UUID);
        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.post400(PATH, dto, "company with id '" + dto.getCompanyId() + "' not found");
        dto.setCompanyId(validCompanyId);

        dto.setDate(null);
        Assert.postValidationError(PATH, dto, NOT_NULL);
        dto.setDate("");
        Assert.postValidationError(PATH, dto, MATCH_DATE_FORMAT);
        dto.setDate("1.1.2020");
        Assert.postValidationError(PATH, dto, MATCH_DATE_FORMAT);
        dto.setDate(validDate);

        dto.setDividend(null);
        Assert.postValidationError(PATH, dto, NOT_NULL);
        dto.setDividend("x");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_5_2_false);
        dto.setDividend(".1");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_5_2_false);
        dto.setDividend("1.");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_5_2_false);
        dto.setDividend("123456");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_5_2_false);
        dto.setDividend("10.123");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_5_2_false);
        dto.setDividend("-1");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_5_2_false);
        dto.setDividend(validDividend);

        dto.setTax(null);
        Assert.postValidationError(PATH, dto, NOT_NULL);
        dto.setTax("x");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_4_2_false);
        dto.setTax(".1");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_4_2_false);
        dto.setTax("1.");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_4_2_false);
        dto.setTax("12345");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_4_2_false);
        dto.setTax("10.123");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_4_2_false);
        dto.setTax("-1");
        Assert.postValidationError(PATH, dto, BIG_DECIMAL_4_2_false);
    }
}
