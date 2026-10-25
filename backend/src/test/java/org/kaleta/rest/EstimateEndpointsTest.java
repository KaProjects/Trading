package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Assert;
import org.kaleta.persistence.api.EstimateDao;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.rest.dto.EstimateCreateDto;
import org.kaleta.rest.dto.EstimateDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_6_2_true;
import static org.kaleta.framework.Assert.ExpectedViolation.MATCH_DATE_FORMAT;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
class EstimateEndpointsTest
{
    private static final String PATH = "/estimate";

    @Inject
    EstimateDao estimateDao;

    @Test
    void getLatest()
    {
        PeriodEstimates dto = given().when()
                .get(PATH + "/1/eps/latest")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().as(PeriodEstimates.class);

        assertThat(dto.getId(), is(5L));
        assertThat(dto.getPeriodId(), is(1L));
        assertThat(dto.getDatetime(), is(LocalDateTime.parse("2026-08-02T12:30:00")));
        assertBigDecimals(dto.getCurrent(), new BigDecimal("41.50"));
        assertBigDecimals(dto.getNext1(), new BigDecimal("42.75"));
        assertBigDecimals(dto.getNext2(), new BigDecimal("44.00"));
        assertBigDecimals(dto.getNext3(), new BigDecimal("45.25"));
    }

    @Test
    void getLatest_empty()
    {
        given().when()
                .get(PATH + "/3/eps/latest")
                .then()
                .statusCode(204);
    }

    @Test
    void getLatest_invalidPeriod()
    {
        Long missingPeriodId = 4_294_967_295L;

        Assert.getValidationError(PATH + "/0/eps/latest", VALID_ID);
        Assert.get400(
                PATH + "/" + missingPeriodId + "/eps/latest",
                "period with id '" + missingPeriodId + "' not found");
    }

    @Test
    void getAll()
    {
        List<EstimateDto> estimates = given().when()
                .get(PATH + "/1/eps")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().body().jsonPath().getList(".", EstimateDto.class);

        assertThat(estimates.size(), is(3));
        assertThat(estimates.get(0).getId(), is(5L));
        assertThat(estimates.get(1).getId(), is(3L));
        assertThat(estimates.get(2).getId(), is(1L));
    }

    @Test
    void create()
    {
        Long periodId = 1839L;
        EstimateCreateDto dto = validDto();

        Assert.post201(PATH + "/" + periodId + "/eps", dto);

        List<Estimate> estimates = estimateDao.list(periodId, Estimate.EPS);
        assertThat(estimates.size(), is(1));
        Estimate estimate = estimates.getFirst();
        assertThat(estimate.getId(), is(notNullValue()));
        assertThat(estimate.getPeriod().getId(), is(periodId));
        assertThat(estimate.getDatetime(), is(LocalDateTime.parse("2026-08-03T00:00:00")));
        assertBigDecimals(estimate.getCurrent(), new BigDecimal(dto.getCurrent()));
        assertBigDecimals(estimate.getNext1(), new BigDecimal(dto.getNext1()));
        assertThat(estimate.getNext2(), is(nullValue()));
        assertBigDecimals(estimate.getNext3(), new BigDecimal(dto.getNext3()));
    }

    @Test
    void create_invalidParameters()
    {
        Long periodId = 3L;
        Long missingPeriodId = 4_294_967_295L;
        EstimateCreateDto dto = validDto();

        Assert.postValidationError(PATH + "/" + periodId + "/eps", null, NOT_NULL);
        Assert.postValidationError(PATH + "/0/eps", dto, VALID_ID);
        Assert.post400(
                PATH + "/" + missingPeriodId + "/eps",
                dto,
                "period with id '" + missingPeriodId + "' not found");

        dto.setDate(null);
        Assert.postValidationError(PATH + "/" + periodId + "/eps", dto, NOT_NULL);
        dto.setDate("03.08.2026");
        Assert.postValidationError(PATH + "/" + periodId + "/eps", dto, MATCH_DATE_FORMAT);
        dto.setDate("2026-08-03");

        dto.setCurrent(null);
        Assert.postValidationError(PATH + "/" + periodId + "/eps", dto, NOT_NULL);
        dto.setCurrent("1234567");
        Assert.postValidationError(PATH + "/" + periodId + "/eps", dto, BIG_DECIMAL_6_2_true);
        dto.setCurrent("11.50");

        dto.setNext1("12.123");
        Assert.postValidationError(PATH + "/" + periodId + "/eps", dto, BIG_DECIMAL_6_2_true);
        dto.setNext1("12.75");

        dto.setNext2("");
        Assert.postValidationError(PATH + "/" + periodId + "/eps", dto, BIG_DECIMAL_6_2_true);
        dto.setNext2(null);

        dto.setNext3("-1234567");
        Assert.postValidationError(PATH + "/" + periodId + "/eps", dto, BIG_DECIMAL_6_2_true);
    }

    @Test
    void createRevenue_isSeparatedFromEps()
    {
        Long periodId = 2L;
        EstimateCreateDto dto = validDto();
        dto.setCurrent("123456.78");
        dto.setNext1("234567.89");

        int epsCountBefore = estimateDao.list(periodId, Estimate.EPS).size();
        Assert.post201(PATH + "/" + periodId + "/revenue", dto);

        List<Estimate> revenueEstimates = estimateDao.list(periodId, Estimate.REVENUE);
        assertThat(revenueEstimates.size(), is(1));
        assertThat(revenueEstimates.getFirst().isType(), is(Estimate.REVENUE));
        assertBigDecimals(revenueEstimates.getFirst().getCurrent(), new BigDecimal(dto.getCurrent()));
        assertThat(estimateDao.list(periodId, Estimate.EPS).size(), is(epsCountBefore));

        List<EstimateDto> eps = given().when()
                .get(PATH + "/" + periodId + "/eps")
                .then()
                .statusCode(200)
                .extract().body().jsonPath().getList(".", EstimateDto.class);
        assertThat(eps.stream().anyMatch(estimate -> !estimate.isType()), is(false));

        List<EstimateDto> revenue = given().when()
                .get(PATH + "/" + periodId + "/revenue")
                .then()
                .statusCode(200)
                .extract().body().jsonPath().getList(".", EstimateDto.class);
        assertThat(revenue.size(), is(1));
        assertThat(revenue.getFirst().isType(), is(Estimate.REVENUE));
    }

    private EstimateCreateDto validDto()
    {
        EstimateCreateDto dto = new EstimateCreateDto();
        dto.setDate("2026-08-03");
        dto.setCurrent("11.50");
        dto.setNext1("12.75");
        dto.setNext3("14.25");
        return dto;
    }
}
