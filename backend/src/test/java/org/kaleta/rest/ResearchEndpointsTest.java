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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.assertBigDecimals;
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

        assertThat(dto.getRecords().size(), is(2));
        assertThat(dto.getRecords().get(0).getTitle(), is("yyy"));
        assertThat(dto.getRecords().get(0).getDate().toString(), is("2022-01-02"));
        assertBigDecimals(dto.getRecords().get(0).getPrice(), new BigDecimal("100"));
        assertThat(dto.getRecords().get(0).getPriceToRevenues(), is(nullValue()));
        assertThat(dto.getRecords().get(0).getPriceToGrossProfit(), is(nullValue()));
        assertThat(dto.getRecords().get(0).getPriceToOperatingIncome(), is(nullValue()));
        assertBigDecimals(dto.getRecords().get(0).getPriceToNetIncome(), new BigDecimal("10.1"));
        assertThat(dto.getRecords().get(0).getDividendYield(), is(nullValue()));
        assertThat(dto.getRecords().get(0).getAsset(), is(nullValue()));

        assertThat(dto.getRecords().get(1).getTitle(), is("xxx"));
        assertThat(dto.getRecords().get(1).getDate().toString(), is("2021-04-05"));
        assertBigDecimals(dto.getRecords().get(1).getPrice(), new BigDecimal("125"));
        assertBigDecimals(dto.getRecords().get(1).getPriceToRevenues(), new BigDecimal("10"));
        assertBigDecimals(dto.getRecords().get(1).getPriceToGrossProfit(), new BigDecimal("50"));
        assertBigDecimals(dto.getRecords().get(1).getPriceToOperatingIncome(), new BigDecimal("100"));
        assertBigDecimals(dto.getRecords().get(1).getPriceToNetIncome(), new BigDecimal("123"));
        assertBigDecimals(dto.getRecords().get(1).getDividendYield(), new BigDecimal("10.12"));
        assertThat(dto.getRecords().get(1).getAsset(), is(notNullValue()));
        assertBigDecimals(dto.getRecords().get(1).getAsset().getQuantity(), new BigDecimal("456"));
        assertBigDecimals(dto.getRecords().get(1).getAsset().getPurchasePrice(), new BigDecimal("75"));
        assertBigDecimals(dto.getRecords().get(1).getAsset().getCurrentPrice(), new BigDecimal("125"));
        assertBigDecimals(dto.getRecords().get(1).getAsset().getProfitPercent(), new BigDecimal("66.67"));
        assertBigDecimals(dto.getRecords().get(1).getAsset().getProfitValue(), new BigDecimal("22800"));

        assertThat(dto.getLatest().getCompany().getId(), is(companyId));
        assertThat(dto.getLatest().getDatetime(), is(LocalDateTime.of(2025, 10, 27, 14, 35)));
        assertBigDecimals(dto.getLatest().getPrice(), new BigDecimal("1234"));

        assertThat(dto.getIndicators().getDatetime(), is(LocalDateTime.of(2025, 10, 27, 14, 35)));
        assertBigDecimals(dto.getIndicators().getPrice(), new BigDecimal("1234"));
        assertBigDecimals(dto.getIndicators().getShares(), new BigDecimal("100"));
        assertBigDecimals(dto.getIndicators().getMarketCap(), new BigDecimal("123400"));
        assertThat(dto.getIndicators().getTtm(), is(notNullValue()));

        assertBigDecimals(dto.getIndicators().getTtm().getMarketCapToRevenues(), new BigDecimal("41.13"));
        assertBigDecimals(dto.getIndicators().getTtm().getMarketCapToGrossProfit(), new BigDecimal("102.83"));
        assertBigDecimals(dto.getIndicators().getTtm().getMarketCapToOperatingIncome(), new BigDecimal("246.8"));
        assertBigDecimals(dto.getIndicators().getTtm().getMarketCapToNetIncome(), new BigDecimal("771.25"));
        assertBigDecimals(dto.getIndicators().getTtm().getDividendYield(), new BigDecimal("0.05"));

        assertThat(dto.getAssets().getAssets().size(), is(2));
        assertBigDecimals(dto.getAssets().getAssets().get(0).getQuantity(), new BigDecimal("10"));
        assertBigDecimals(dto.getAssets().getAssets().get(0).getPurchasePrice(), new BigDecimal("90"));
        assertBigDecimals(dto.getAssets().getAssets().get(0).getCurrentPrice(), dto.getIndicators().getPrice());
        assertBigDecimals(dto.getAssets().getAssets().get(0).getProfitValue(), new BigDecimal("11440"));
        assertBigDecimals(dto.getAssets().getAssets().get(0).getProfitPercent(), new BigDecimal("1271.11"));
        assertBigDecimals(dto.getAssets().getAssets().get(1).getQuantity(), new BigDecimal("20"));
        assertBigDecimals(dto.getAssets().getAssets().get(1).getPurchasePrice(), new BigDecimal("180"));
        assertBigDecimals(dto.getAssets().getAssets().get(1).getCurrentPrice(), dto.getIndicators().getPrice());
        assertBigDecimals(dto.getAssets().getAssets().get(1).getProfitValue(), new BigDecimal("21080"));
        assertBigDecimals(dto.getAssets().getAssets().get(1).getProfitPercent(), new BigDecimal("585.56"));

        assertThat(dto.getAssets().getAggregate(), is(notNullValue()));
        assertBigDecimals(dto.getAssets().getAggregate().getQuantity(), new BigDecimal("30"));
        assertBigDecimals(dto.getAssets().getAggregate().getCurrentPrice(), new BigDecimal("1234"));
        assertBigDecimals(dto.getAssets().getAggregate().getPurchasePrice(), new BigDecimal("150"));
        assertBigDecimals(dto.getAssets().getAggregate().getProfitValue(), new BigDecimal("32520"));
        assertBigDecimals(dto.getAssets().getAggregate().getProfitPercent(), new BigDecimal("722.67"));
    }

    @Test
    void get_invalidParameters()
    {
        Assert.getValidationError("/research/" + "AAAAAA", "must be a valid UUID");

        String randomUuid = UUID.randomUUID().toString();
        Assert.get400("/research/" + randomUuid, "company with id '" + randomUuid + "' not found");
    }
}
