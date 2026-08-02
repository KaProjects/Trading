package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Assert;
import org.kaleta.persistence.api.EstimateDao;
import org.kaleta.persistence.entity.Estimate;
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
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_4_2_true;
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
        EstimateDto dto = given().when()
                .get(PATH + "/1/latest")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().as(EstimateDto.class);

        assertThat(dto.getId(), is(3L));
        assertThat(dto.getPeriodId(), is(1L));
        assertThat(dto.getDatetime(), is(LocalDateTime.parse("2026-08-02T12:30:00")));
        assertBigDecimals(dto.getTtm(), new BigDecimal("30.25"));
        assertBigDecimals(dto.getCurrent(), new BigDecimal("31.50"));
        assertBigDecimals(dto.getNext1(), new BigDecimal("32.75"));
        assertBigDecimals(dto.getNext2(), new BigDecimal("34.00"));
        assertBigDecimals(dto.getNext3(), new BigDecimal("35.25"));
    }

    @Test
    void getLatest_empty()
    {
        given().when()
                .get(PATH + "/3/latest")
                .then()
                .statusCode(204);
    }

    @Test
    void getLatest_invalidPeriod()
    {
        Long missingPeriodId = 4_294_967_295L;

        Assert.getValidationError(PATH + "/0/latest", VALID_ID);
        Assert.get400(
                PATH + "/" + missingPeriodId + "/latest",
                "period with id '" + missingPeriodId + "' not found");
    }

    @Test
    void create()
    {
        Long periodId = 1839L;
        EstimateCreateDto dto = validDto();

        Assert.post201(PATH + "/" + periodId, dto);

        List<Estimate> estimates = estimateDao.list(periodId);
        assertThat(estimates.size(), is(1));
        Estimate estimate = estimates.getFirst();
        assertThat(estimate.getId(), is(notNullValue()));
        assertThat(estimate.getPeriod().getId(), is(periodId));
        assertThat(estimate.getDatetime(), is(notNullValue()));
        assertBigDecimals(estimate.getTtm(), new BigDecimal(dto.getTtm()));
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

        Assert.postValidationError(PATH + "/" + periodId, null, NOT_NULL);
        Assert.postValidationError(PATH + "/0", dto, VALID_ID);
        Assert.post400(
                PATH + "/" + missingPeriodId,
                dto,
                "period with id '" + missingPeriodId + "' not found");

        dto.setTtm(null);
        Assert.postValidationError(PATH + "/" + periodId, dto, NOT_NULL);
        dto.setTtm("x");
        Assert.postValidationError(PATH + "/" + periodId, dto, BIG_DECIMAL_4_2_true);
        dto.setTtm("10.25");

        dto.setCurrent(null);
        Assert.postValidationError(PATH + "/" + periodId, dto, NOT_NULL);
        dto.setCurrent("12345");
        Assert.postValidationError(PATH + "/" + periodId, dto, BIG_DECIMAL_4_2_true);
        dto.setCurrent("11.50");

        dto.setNext1("12.123");
        Assert.postValidationError(PATH + "/" + periodId, dto, BIG_DECIMAL_4_2_true);
        dto.setNext1("12.75");

        dto.setNext2("");
        Assert.postValidationError(PATH + "/" + periodId, dto, BIG_DECIMAL_4_2_true);
        dto.setNext2(null);

        dto.setNext3("-12345");
        Assert.postValidationError(PATH + "/" + periodId, dto, BIG_DECIMAL_4_2_true);
    }

    private EstimateCreateDto validDto()
    {
        EstimateCreateDto dto = new EstimateCreateDto();
        dto.setTtm("-10.25");
        dto.setCurrent("11.50");
        dto.setNext1("12.75");
        dto.setNext3("14.25");
        return dto;
    }
}
