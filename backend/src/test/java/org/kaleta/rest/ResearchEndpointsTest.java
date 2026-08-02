package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.client.FinnhubClient;
import org.kaleta.client.PolygonClient;
import org.kaleta.client.RequestFailureException;
import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonPriceRange;
import org.kaleta.framework.Assert;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.EstimateImportDto;
import org.kaleta.rest.dto.PeriodImportCandidateDto;
import org.kaleta.rest.dto.PeriodImportDataDto;
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.dto.ResearchDto;
import org.kaleta.service.FirebaseService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class ResearchEndpointsTest
{
    @InjectMock
    FinnhubClient finnhubClient;
    @InjectMock
    PolygonClient polygonClient;
    @InjectMock
    @MockitoConfig(convertScopes = true)
    FirebaseService firebaseService;

    @BeforeEach
    void before() throws RequestFailureException
    {
        reset(finnhubClient, polygonClient, firebaseService);
        when(finnhubClient.quote(any())).thenReturn(null);
        when(firebaseService.getPeriod(anyString(), anyString())).thenReturn(null);
        when(firebaseService.getNewerPeriods(anyString(), nullable(String.class))).thenReturn(List.of());
    }

    @Test
    void get() {
        Long companyId = 2281L;
        ResearchDto dto = given().when()
                .get("/research/" + companyId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", ResearchDto.class);


        assertThat(dto.getCompany().getId(), is(companyId));
        assertThat(dto.getCompany().getTicker(), is("RCH"));
        assertThat(dto.getCompany().getWatching(), is(true));
        assertThat(dto.getCompany().getCurrency(), is(Currency.$));

        assertThat(dto.getPeriods().size(), is(3));
        assertThat(dto.getPeriods().get(0).getName(), is(PeriodName.valueOf("25Q1")));
        assertThat(dto.getPeriods().get(1).getName(), is(PeriodName.valueOf("24Q4")));
        assertThat(dto.getPeriods().get(2).getName(), is(PeriodName.valueOf("24Q3")));
        assertThat(dto.getPeriods().get(0).getCachedData(), is(nullValue()));
        assertThat(dto.getPeriods().get(1).getCachedData(), is(nullValue()));
        assertThat(dto.getPeriods().get(2).getCachedData(), is(nullValue()));
        assertThat(dto.getPeriods().get(0).getEstimate(), is(notNullValue()));
        assertBigDecimals(dto.getPeriods().get(0).getEstimate().getCurrent(), new BigDecimal("1.62"));
        assertBigDecimals(dto.getPeriods().get(0).getEstimate().getNext1(), new BigDecimal("1.85"));
        assertThat(dto.getPeriods().get(0).getEstimate().getNext2(), is(nullValue()));
        assertThat(dto.getPeriods().get(0).getEstimate().getNext3(), is(nullValue()));
        assertThat(dto.getPeriods().get(1).getEstimate(), is(nullValue()));
        assertThat(dto.getPeriods().get(2).getEstimate(), is(nullValue()));
        assertThat(dto.getFinancials().size(), is(2));
        assertThat(dto.getFinancials().get(0).getPeriod(), is(PeriodName.valueOf("24Q4")));
        assertThat(dto.getFinancials().get(1).getPeriod(), is(PeriodName.valueOf("24Q3")));
        assertThat(dto.getTtm(), is(notNullValue()));

        assertThat(dto.getRecords().size(), is(2));
        assertThat(dto.getRecords().get(0).getTitle(), is("yyy"));
        assertThat(dto.getRecords().get(0).getDate().toString(), is("2022-01-02"));
        assertBigDecimals(dto.getRecords().get(0).getPrice(), new BigDecimal("100"));
        assertThat(dto.getRecords().get(0).getPriceToRevenues(), is(nullValue()));
        assertThat(dto.getRecords().get(0).getPriceToGrossProfit(), is(nullValue()));
        assertThat(dto.getRecords().get(0).getPriceToOperatingIncome(), is(nullValue()));
        assertBigDecimals(dto.getRecords().get(0).getPriceToNetIncome(), new BigDecimal("10.1"));
        assertThat(dto.getRecords().get(0).getDividendYield(), is(nullValue()));
        assertThat(dto.getRecords().get(0).getTargets(), is(nullValue()));
        assertThat(dto.getRecords().get(0).getAsset(), is(nullValue()));

        assertThat(dto.getRecords().get(1).getTitle(), is("xxx"));
        assertThat(dto.getRecords().get(1).getDate().toString(), is("2021-04-05"));
        assertBigDecimals(dto.getRecords().get(1).getPrice(), new BigDecimal("125"));
        assertBigDecimals(dto.getRecords().get(1).getPriceToRevenues(), new BigDecimal("10"));
        assertBigDecimals(dto.getRecords().get(1).getPriceToGrossProfit(), new BigDecimal("50"));
        assertBigDecimals(dto.getRecords().get(1).getPriceToOperatingIncome(), new BigDecimal("100"));
        assertBigDecimals(dto.getRecords().get(1).getPriceToNetIncome(), new BigDecimal("123"));
        assertBigDecimals(dto.getRecords().get(1).getDividendYield(), new BigDecimal("10.12"));
        assertThat(dto.getRecords().get(1).getTargets(), is(nullValue()));
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
        assertBigDecimals(dto.getIndicators().getTtm().getMarketCapToGrossProfit(), new BigDecimal("68.56"));
        assertBigDecimals(dto.getIndicators().getTtm().getMarketCapToOperatingIncome(), new BigDecimal("176.29"));
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

        assertThat(dto.getImportablePeriods(), is(notNullValue()));
        assertThat(dto.getImportablePeriods().size(), is(0));
    }

    @Test
    void get_invalidParameters()
    {
        Assert.getValidationError("/research/0", VALID_ID);

        Long randomId = 4_294_967_295L;
        Assert.get400("/research/" + randomId, "company with id '" + randomId + "' not found");
    }

    @Test
    void importPeriod() throws RequestFailureException
    {
        Long companyId = 2281L;
        PeriodImportCandidateDto candidate = candidate("25Q2", "2025-07", true);
        PeriodImportDto firebaseData = firebaseData();
        when(firebaseService.getNewerPeriods("RCH", "25Q1")).thenReturn(List.of(candidate));
        when(firebaseService.getPeriod("RCH", "25Q2")).thenReturn(firebaseData);
        when(polygonClient.getFinancials("RCH", "2025", "Q2")).thenReturn(Optional.of(
                new PolygonFinancials(
                        new BigDecimal("10000000"),
                        new BigDecimal("20000000"),
                        new BigDecimal("30000000"),
                        new BigDecimal("40000000"),
                        new BigDecimal("50000000"))));
        when(polygonClient.getPriceRange("RCH", "2025-05-28", "2025-08-27"))
                .thenReturn(Optional.of(new PolygonPriceRange(
                        new BigDecimal("140.25"),
                        new BigDecimal("90.75"))));

        PeriodImportDataDto dto = given().when()
                .get("/research/" + companyId + "/import/period/25Q2")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", PeriodImportDataDto.class);

        assertThat(dto.getName(), is("25Q2"));
        assertThat(dto.getEndingMonth(), is("2025-07"));
        assertThat(dto.getReportDate(), is("2025-08-27"));
        assertThat(dto.getIsReported(), is(true));
        assertThat(dto.getFirebase().getShares(), is("101"));
        assertThat(dto.getFirebase().getRevenue(), is("201"));
        assertThat(dto.getFirebase().getDividend(), is("6"));
        assertThat(dto.getPolygon().getShares(), is("10"));
        assertThat(dto.getPolygon().getRevenue(), is("20"));
        assertThat(dto.getPolygon().getGrossProfit(), is("30"));
        assertThat(dto.getPolygon().getOperatingIncome(), is("40"));
        assertThat(dto.getPolygon().getNetIncome(), is("50"));
        assertThat(dto.getPolygon().getPriceHigh(), is("140.25"));
        assertThat(dto.getPolygon().getPriceLow(), is("90.75"));
        assertThat(dto.getWarnings().size(), is(0));
        verify(firebaseService).getPeriod("RCH", "25Q2");
        verify(polygonClient).getFinancials("RCH", "2025", "Q2");
        verify(polygonClient).getPriceRange("RCH", "2025-05-28", "2025-08-27");
    }

    @Test
    void importPeriod_polygonFailureReturnsFirebaseDataAndWarning() throws RequestFailureException
    {
        Long companyId = 2281L;
        when(firebaseService.getNewerPeriods("RCH", "25Q1"))
                .thenReturn(List.of(candidate("25Q2", "2025-07", true)));
        when(firebaseService.getPeriod("RCH", "25Q2")).thenReturn(firebaseData());
        when(polygonClient.getFinancials("RCH", "2025", "Q2"))
                .thenThrow(new RequestFailureException("rate limit exceeded"));
        when(polygonClient.getPriceRange("RCH", "2025-05-28", "2025-08-27"))
                .thenReturn(Optional.of(new PolygonPriceRange(
                        new BigDecimal("140.25"),
                        new BigDecimal("90.75"))));

        PeriodImportDataDto dto = given().when()
                .get("/research/" + companyId + "/import/period/25Q2")
                .then()
                .statusCode(200)
                .extract().response().jsonPath().getObject("", PeriodImportDataDto.class);

        assertThat(dto.getFirebase().getRevenue(), is("201"));
        assertThat(dto.getPolygon().getRevenue(), is(nullValue()));
        assertThat(dto.getPolygon().getPriceHigh(), is("140.25"));
        assertThat(dto.getWarnings().size(), is(1));
        assertThat(dto.getWarnings().get(0), is(
                "Polygon.io financial data could not be loaded: rate limit exceeded"));
    }

    @Test
    void importPeriod_invalidParameters()
    {
        Long companyId = 2281L;
        Assert.getValidationError(
                "/research/0/import/period/25Q2",
                VALID_ID);
        Assert.getValidationError(
                "/research/" + companyId + "/import/period/INVALID",
                "must be a valid PeriodName");
        Assert.get400(
                "/research/" + companyId + "/import/period/25Q2",
                "period '25Q2' is not available for import");
    }

    @Test
    void importEstimate()
    {
        Long companyId = 2281L;
        Long periodId = 2182L;
        when(firebaseService.getLatestEstimate("RCH", "24Q1"))
                .thenReturn(estimate("1", "2024-05-01"));
        when(firebaseService.getLatestEstimate("RCH", "24Q3"))
                .thenReturn(estimate("3", "2024-11-01"));
        when(firebaseService.getLatestEstimate("RCH", "24Q4"))
                .thenReturn(estimate("4", "2025-02-01"));
        when(firebaseService.getLatestEstimate("RCH", "25Q1"))
                .thenReturn(estimate("5", "2025-05-01"));
        when(firebaseService.getLatestEstimate("RCH", "25Q2"))
                .thenReturn(estimate("6", "2025-08-01"));
        when(firebaseService.getLatestEstimate("RCH", "25Q3"))
                .thenReturn(estimate("7", "2025-11-01"));
        when(firebaseService.getLatestEstimate("RCH", "25Q4"))
                .thenReturn(estimate("8", "2026-02-01"));

        io.restassured.response.Response response = given().when()
                .get("/research/" + companyId + "/import/estimate/" + periodId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response();
        Map<String, Object> fields = response.jsonPath().getMap("");
        EstimateImportDto dto = response.as(EstimateImportDto.class);

        assertThat(fields.keySet(), containsInAnyOrder(
                "past4", "past3", "past2", "past1", "current", "next1", "next2", "next3"));
        assertThat(dto.getPast4().getEps(), is("1"));
        assertThat(dto.getPast4().getDate(), is("2024-05-01"));
        assertThat(dto.getPast3(), is(nullValue()));
        assertThat(dto.getPast2().getEps(), is("3"));
        assertThat(dto.getPast1().getEps(), is("4"));
        assertThat(dto.getCurrent().getEps(), is("5"));
        assertThat(dto.getNext1().getEps(), is("6"));
        assertThat(dto.getNext2().getEps(), is("7"));
        assertThat(dto.getNext3().getEps(), is("8"));
    }

    @Test
    void importEstimate_invalidParameters()
    {
        Long companyId = 2281L;
        Long periodId = 2182L;
        Long missingId = 4_294_967_295L;

        Assert.getValidationError(
                "/research/0/import/estimate/" + periodId,
                VALID_ID);
        Assert.getValidationError(
                "/research/" + companyId + "/import/estimate/0",
                VALID_ID);
        Assert.get400(
                "/research/" + missingId + "/import/estimate/" + periodId,
                "company with id '" + missingId + "' not found");
        Assert.get400(
                "/research/" + companyId + "/import/estimate/" + missingId,
                "period with id '" + missingId + "' not found");
        Assert.get400(
                "/research/1927/import/estimate/" + periodId,
                "period with id '" + periodId + "' does not belong to company with id '1927'");
    }

    private PeriodImportCandidateDto candidate(String name, String endingMonth, boolean reported)
    {
        PeriodImportCandidateDto candidate = new PeriodImportCandidateDto();
        candidate.setName(name);
        candidate.setEndingMonth(endingMonth);
        candidate.setIsReported(reported);
        return candidate;
    }

    private EstimateImportDto.Quarter estimate(String eps, String date)
    {
        EstimateImportDto.Quarter estimate = new EstimateImportDto.Quarter();
        estimate.setEps(eps);
        estimate.setDate(date);
        return estimate;
    }

    private PeriodImportDto firebaseData()
    {
        PeriodImportDto data = new PeriodImportDto();
        data.setName("25Q2");
        data.setEndingMonth("2025-07");
        data.setReportDate("2025-08-27");
        data.setPreviousReportDate("2025-05-28");
        data.setIsReported(true);
        data.setShares("101");
        data.setRevenue("201");
        data.setGrossProfit("102");
        data.setOperatingIncome("51");
        data.setNetIncome("31");
        data.setDividend("6");
        data.setPriceHigh("151.5");
        data.setPriceLow("81.5");
        return data;
    }
}
