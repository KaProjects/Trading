package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.client.FinnhubClient;
import org.kaleta.client.RequestFailureException;
import org.kaleta.framework.Assert;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.ResearchDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
public class ResearchEndpointsTest
{
    @InjectMock
    FinnhubClient finnhubClient;

    @BeforeEach
    void before() throws RequestFailureException
    {
        when(finnhubClient.quote(any())).thenReturn(null);
    }

    @Test
    void get() {
        String companyId = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        ResearchDto dto = given().when()
                .get("/research/" + companyId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", ResearchDto.class);


        assertThat(dto.getCompany().getId(), is(companyId));
        assertThat(dto.getCompany().getTicker(), is("RCH"));
        assertThat(dto.getCompany().getShares(), is(nullValue()));
        assertThat(dto.getCompany().getWatching(), is(true));
        assertThat(dto.getCompany().getCurrency(), is(Currency.$));

        assertThat(dto.getPeriods().getPeriods().size(), is(3));
        assertThat(dto.getPeriods().getPeriods().get(0).getName(), is(PeriodName.valueOf("25Q1")));
        assertThat(dto.getPeriods().getPeriods().get(1).getName(), is(PeriodName.valueOf("24Q4")));
        assertThat(dto.getPeriods().getPeriods().get(2).getName(), is(PeriodName.valueOf("24Q3")));
        assertThat(dto.getPeriods().getFinancials().size(), is(2));
        assertThat(dto.getPeriods().getFinancials().get(0).getPeriod(), is(PeriodName.valueOf("24Q4")));
        assertThat(dto.getPeriods().getFinancials().get(1).getPeriod(), is(PeriodName.valueOf("24Q3")));
        assertThat(dto.getPeriods().getTtm(), is(notNullValue()));

        // TODO list of records

        assertThat(dto.getLatest().getCompany().getId(), is(companyId));
        assertThat(dto.getLatest().getDatetime(), is(LocalDateTime.of(2025, 10, 27, 14, 35)));
        assertThat(dto.getLatest().getPrice(), comparesEqualTo(new BigDecimal("1234")));

        assertThat(dto.getPriceRatios().getDatetime(), is(LocalDateTime.of(2025, 10, 27, 14, 35)));
        assertThat(dto.getPriceRatios().getPrice(), comparesEqualTo(new BigDecimal("1234")));
        assertThat(dto.getPriceRatios().getShares(), comparesEqualTo(new BigDecimal("100")));
        assertThat(dto.getPriceRatios().getMarketCap(), comparesEqualTo(new BigDecimal("123400")));
        assertThat(dto.getPriceRatios().getTtm(), is(notNullValue()));

        assertThat(dto.getPriceRatios().getTtm().getMarketCapToRevenues(), comparesEqualTo(new BigDecimal("41.13")));
        assertThat(dto.getPriceRatios().getTtm().getMarketCapToGrossIncome(), comparesEqualTo(new BigDecimal("102.83")));
        assertThat(dto.getPriceRatios().getTtm().getMarketCapToOperatingIncome(), comparesEqualTo(new BigDecimal("246.8")));
        assertThat(dto.getPriceRatios().getTtm().getMarketCapToNetIncome(), comparesEqualTo(new BigDecimal("771.25")));
        assertThat(dto.getPriceRatios().getTtm().getDividendYield(), comparesEqualTo(new BigDecimal("0.05")));

        assertThat(dto.getAssets().size(), is(2));
        assertThat(dto.getAssets().get(0).getQuantity(), comparesEqualTo(new BigDecimal("10")));
        assertThat(dto.getAssets().get(0).getPurchasePrice(), comparesEqualTo(new BigDecimal("90")));
        assertThat(dto.getAssets().get(0).getCurrentPrice(), comparesEqualTo(dto.getLatest().getPrice()));
        assertThat(dto.getAssets().get(0).getProfitValue(), comparesEqualTo(new BigDecimal("11440")));
        assertThat(dto.getAssets().get(0).getProfitPercent(), comparesEqualTo(new BigDecimal("1271.11")));
        assertThat(dto.getAssets().get(1).getQuantity(), comparesEqualTo(new BigDecimal("20")));
        assertThat(dto.getAssets().get(1).getPurchasePrice(), comparesEqualTo(new BigDecimal("180")));
        assertThat(dto.getAssets().get(1).getCurrentPrice(), comparesEqualTo(dto.getLatest().getPrice()));
        assertThat(dto.getAssets().get(1).getProfitValue(), comparesEqualTo(new BigDecimal("21080")));
        assertThat(dto.getAssets().get(1).getProfitPercent(), comparesEqualTo(new BigDecimal("585.56")));
    }

    @Test
    void getInvalidValues()
    {
        Assert.getValidationError("/research/" + "AAAAAA", "must be a valid UUID");

        String randomUuid = UUID.randomUUID().toString();
        Assert.get400("/research/" + randomUuid, "company with id '" + randomUuid + "' not found");
    }
}
