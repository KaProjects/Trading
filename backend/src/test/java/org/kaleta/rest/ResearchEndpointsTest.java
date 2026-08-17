package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.client.AlphaVantageClient;
import org.kaleta.client.FinnhubClient;
import org.kaleta.client.PolygonClient;
import org.kaleta.client.RequestFailureException;
import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;
import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonPriceRange;
import org.kaleta.framework.Assert;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.EstimateImportDto;
import org.kaleta.rest.dto.PeriodImportDataDto;
import org.kaleta.rest.dto.PeriodImportCandidateDto;
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
import static org.mockito.Mockito.inOrder;
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
    AlphaVantageClient alphaVantageClient;
    @InjectMock
    @MockitoConfig(convertScopes = true)
    FirebaseService firebaseService;

    @BeforeEach
    void before() throws RequestFailureException
    {
        reset(finnhubClient, polygonClient, alphaVantageClient, firebaseService);
        when(finnhubClient.quote(any())).thenReturn(null);
        when(firebaseService.getPeriod(anyString(), anyString())).thenReturn(null);
        when(firebaseService.getNewerPeriods(anyString(), nullable(String.class)))
                .thenReturn(new FirebaseService.ImportCandidatesResult(List.of(), List.of()));
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
        assertThat(dto.getCompany().getCurrency(), is(Currency.$));

        assertThat(dto.getPeriods().size(), is(3));
        assertThat(dto.getPeriods().get(0).getName(), is(PeriodName.valueOf("25Q1")));
        assertThat(dto.getPeriods().get(1).getName(), is(PeriodName.valueOf("24Q4")));
        assertThat(dto.getPeriods().get(2).getName(), is(PeriodName.valueOf("24Q3")));
        assertThat(dto.getPeriods().get(0).getEstimate(), is(notNullValue()));
        assertBigDecimals(dto.getPeriods().get(0).getEstimate().getCurrent(), new BigDecimal("1.62"));
        assertBigDecimals(dto.getPeriods().get(0).getEstimate().getNext1(), new BigDecimal("1.85"));
        assertThat(dto.getPeriods().get(0).getEstimate().getNext2(), is(nullValue()));
        assertThat(dto.getPeriods().get(0).getEstimate().getNext3(), is(nullValue()));
        assertThat(dto.getPeriods().get(1).getEstimate(), is(nullValue()));
        assertThat(dto.getPeriods().get(2).getEstimate(), is(nullValue()));
        assertThat(dto.getEstimateOverview(), is(notNullValue()));
        assertThat(dto.getEstimateOverview().getTtm().getValue(), is(nullValue()));
        assertThat(dto.getEstimateOverview().getCurrent().getValue(), is(nullValue()));
        assertThat(dto.getEstimateOverview().getNext1().getValue(), is(nullValue()));
        assertThat(dto.getEstimateOverview().getNext2().getValue(), is(nullValue()));
        assertThat(dto.getEstimateOverview().getNext3().getValue(), is(nullValue()));
        assertThat(dto.getFinancials().size(), is(2));
        assertThat(dto.getFinancials().get(0).getPeriod(), is(PeriodName.valueOf("24Q4")));
        assertBigDecimals(dto.getFinancials().get(0).getCapex().getValue(), new BigDecimal("-40"));
        assertBigDecimals(dto.getFinancials().get(0).getFreeCashFlow().getValue(), new BigDecimal("60"));
        assertBigDecimals(dto.getFinancials().get(0).getAdjustedEps(), new BigDecimal("1.25"));
        assertThat(dto.getFinancials().get(1).getPeriod(), is(PeriodName.valueOf("24Q3")));
        assertThat(dto.getFinancials().get(1).getAdjustedEps(), is(nullValue()));
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
        assertThat(dto.getWarnings(), is(List.of()));

        verify(firebaseService).getNewerPeriods("RCH", "25Q1");
    }

    @Test
    void getIncludesImportablePeriodNames()
    {
        PeriodImportCandidateDto candidate = new PeriodImportCandidateDto();
        candidate.setName("25Q2");
        candidate.setEndingMonth("2025-07");
        candidate.setIsReported(false);
        when(firebaseService.getNewerPeriods("RCH", "25Q1"))
                .thenReturn(new FirebaseService.ImportCandidatesResult(List.of(candidate), List.of()));

        ResearchDto dto = given().when()
                .get("/research/2281")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", ResearchDto.class);

        assertThat(dto.getImportablePeriods().size(), is(1));
        assertThat(dto.getImportablePeriods().get(0).getName(), is("25Q2"));
        assertThat(dto.getImportablePeriods().get(0).getEndingMonth(), is("2025-07"));
        assertThat(dto.getImportablePeriods().get(0).getIsReported(), is(false));
        verify(firebaseService).getNewerPeriods("RCH", "25Q1");
    }

    @Test
    void get_externalFailuresReturnWarningsAndAvailableData() throws RequestFailureException
    {
        when(finnhubClient.quote("RCH"))
                .thenThrow(new RequestFailureException("daily limit exceeded"));
        when(firebaseService.getNewerPeriods("RCH", "25Q1"))
                .thenReturn(new FirebaseService.ImportCandidatesResult(
                        List.of(),
                        List.of("Firebase import candidates for RCH could not be loaded: permission denied")));

        ResearchDto dto = given().when()
                .get("/research/2281")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().as(ResearchDto.class);

        assertThat(dto.getLatest(), is(notNullValue()));
        assertThat(dto.getImportablePeriods(), is(List.of()));
        assertThat(dto.getWarnings(), containsInAnyOrder(
                "Finnhub quote for RCH could not be loaded: daily limit exceeded",
                "Firebase import candidates for RCH could not be loaded: permission denied"));
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
        PeriodImportDto firebaseData = firebaseData();
        when(firebaseService.getPeriod("RCH", "25Q2")).thenReturn(firebaseData);
        when(firebaseService.getLatestActualEps("RCH", "25Q2")).thenReturn("1.27");
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
        when(alphaVantageClient.getIncomeStatement("RCH", "25Q2", "2025-07"))
                .thenReturn(Optional.of(new AlphaVantageIncomeStatement(
                        new BigDecimal("21000000"),
                        new BigDecimal("31000000"),
                        new BigDecimal("41000000"),
                        new BigDecimal("51000000"))));
        when(alphaVantageClient.getCashFlow("RCH", "25Q2", "2025-07"))
                .thenReturn(Optional.of(new AlphaVantageCashFlow(
                        new BigDecimal("7000000"),
                        new BigDecimal("12000000"),
                        new BigDecimal("22000000"))));

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
        assertThat(dto.getFirebase().getCapex(), is("-11"));
        assertThat(dto.getFirebase().getFreeCashFlow(), is("21"));
        assertThat(dto.getFirebase().getAdjustedEps(), is("1.25"));
        assertThat(dto.getPolygon().getShares(), is("10"));
        assertThat(dto.getPolygon().getRevenue(), is("20"));
        assertThat(dto.getPolygon().getGrossProfit(), is("30"));
        assertThat(dto.getPolygon().getOperatingIncome(), is("40"));
        assertThat(dto.getPolygon().getNetIncome(), is("50"));
        assertThat(dto.getPolygon().getAdjustedEps(), is("1.27"));
        assertThat(dto.getPolygon().getPriceHigh(), is("140.25"));
        assertThat(dto.getPolygon().getPriceLow(), is("90.75"));
        assertThat(dto.getAlphaVantage().getRevenue(), is("21"));
        assertThat(dto.getAlphaVantage().getGrossProfit(), is("31"));
        assertThat(dto.getAlphaVantage().getOperatingIncome(), is("41"));
        assertThat(dto.getAlphaVantage().getNetIncome(), is("51"));
        assertThat(dto.getAlphaVantage().getDividend(), is("7"));
        assertThat(dto.getAlphaVantage().getCapex(), is("12"));
        assertThat(dto.getAlphaVantage().getFreeCashFlow(), is("22"));
        assertThat(dto.getWarnings().size(), is(0));
        verify(firebaseService).getPeriod("RCH", "25Q2");
        verify(firebaseService).getLatestActualEps("RCH", "25Q2");
        verify(polygonClient).getFinancials("RCH", "2025", "Q2");
        verify(polygonClient).getPriceRange("RCH", "2025-05-28", "2025-08-27");
        var alphaVantageOrder = inOrder(alphaVantageClient);
        alphaVantageOrder.verify(alphaVantageClient).getCashFlow("RCH", "25Q2", "2025-07");
        alphaVantageOrder.verify(alphaVantageClient).getIncomeStatement("RCH", "25Q2", "2025-07");
    }

    @Test
    void importPeriod_sourceFailureReturnsAvailableSuggestionsWithWarnings() throws RequestFailureException
    {
        Long companyId = 2281L;
        when(firebaseService.getPeriod("RCH", "25Q2")).thenReturn(firebaseData());
        when(firebaseService.getLatestActualEps("RCH", "25Q2"))
                .thenThrow(new IllegalStateException("Finnhub unavailable"));
        when(polygonClient.getFinancials("RCH", "2025", "Q2"))
                .thenThrow(new RequestFailureException("rate limit exceeded"));
        when(polygonClient.getPriceRange("RCH", "2025-05-28", "2025-08-27"))
                .thenReturn(Optional.of(new PolygonPriceRange(
                        new BigDecimal("140.25"),
                        new BigDecimal("90.75"))));
        when(alphaVantageClient.getIncomeStatement("RCH", "25Q2", "2025-07"))
                .thenThrow(new RequestFailureException("daily limit reached"));
        when(alphaVantageClient.getCashFlow("RCH", "25Q2", "2025-07"))
                .thenReturn(Optional.of(new AlphaVantageCashFlow(
                        new BigDecimal("7000000"),
                        new BigDecimal("12000000"),
                        new BigDecimal("22000000"))));

        PeriodImportDataDto dto = given().when()
                .get("/research/" + companyId + "/import/period/25Q2")
                .then()
                .statusCode(200)
                .extract().response().jsonPath().getObject("", PeriodImportDataDto.class);

        assertThat(dto.getFirebase().getRevenue(), is("201"));
        assertThat(dto.getPolygon().getRevenue(), is(nullValue()));
        assertThat(dto.getPolygon().getAdjustedEps(), is(nullValue()));
        assertThat(dto.getPolygon().getPriceHigh(), is("140.25"));
        assertThat(dto.getAlphaVantage().getRevenue(), is(nullValue()));
        assertThat(dto.getAlphaVantage().getDividend(), is("7"));
        assertThat(dto.getAlphaVantage().getFreeCashFlow(), is("22"));
        assertThat(dto.getWarnings(), containsInAnyOrder(
                "Polygon.io financial data for 25Q2 and RCH could not be loaded: rate limit exceeded",
                "Alpha Vantage income statement for 25Q2 and RCH could not be loaded: daily limit reached",
                "Firebase/Finnhub EPS for 25Q2 and RCH could not be loaded: Finnhub unavailable"));
    }

    @Test
    void importPeriod_missingGeminiUsesProvidedEndingMonthForExternalSuggestions() throws RequestFailureException
    {
        Long companyId = 2281L;
        when(firebaseService.getPeriod("RCH", "25Q2"))
                .thenThrow(new IllegalStateException("Gemini unavailable"));
        when(polygonClient.getFinancials("RCH", "2025", "Q2")).thenReturn(Optional.of(
                new PolygonFinancials(
                        new BigDecimal("10000000"),
                        new BigDecimal("20000000"),
                        null,
                        null,
                        null)));
        when(alphaVantageClient.getCashFlow("RCH", "25Q2", "2025-07"))
                .thenReturn(Optional.of(new AlphaVantageCashFlow(
                        new BigDecimal("7000000"),
                        new BigDecimal("12000000"),
                        new BigDecimal("22000000"))));
        when(alphaVantageClient.getIncomeStatement("RCH", "25Q2", "2025-07"))
                .thenReturn(Optional.of(new AlphaVantageIncomeStatement(
                        new BigDecimal("21000000"),
                        new BigDecimal("31000000"),
                        new BigDecimal("41000000"),
                        new BigDecimal("51000000"))));
        when(firebaseService.getLatestActualEps("RCH", "25Q2")).thenReturn("1.27");

        PeriodImportDataDto dto = given().when()
                .get("/research/" + companyId + "/import/period/25Q2?endingMonth=2025-07")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", PeriodImportDataDto.class);

        assertThat(dto.getFirebase().getRevenue(), is(nullValue()));
        assertThat(dto.getPolygon().getShares(), is("10"));
        assertThat(dto.getPolygon().getRevenue(), is("20"));
        assertThat(dto.getPolygon().getAdjustedEps(), is("1.27"));
        assertThat(dto.getPolygon().getPriceHigh(), is(nullValue()));
        assertThat(dto.getAlphaVantage().getRevenue(), is("21"));
        assertThat(dto.getAlphaVantage().getCapex(), is("12"));
        assertThat(dto.getAlphaVantage().getFreeCashFlow(), is("22"));
        assertThat(dto.getWarnings(), is(List.of(
                "Firebase period 25Q2 for RCH could not be loaded: Gemini unavailable")));
        verify(polygonClient).getFinancials("RCH", "2025", "Q2");
        verify(alphaVantageClient).getCashFlow("RCH", "25Q2", "2025-07");
        verify(alphaVantageClient).getIncomeStatement("RCH", "25Q2", "2025-07");
        verify(firebaseService).getLatestActualEps("RCH", "25Q2");
    }

    @Test
    void importPeriod_withoutAnySuggestionsReturnsEmptyData()
    {
        Long companyId = 2281L;

        PeriodImportDataDto dto = given().when()
                .get("/research/" + companyId + "/import/period/25Q2")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", PeriodImportDataDto.class);

        assertThat(dto.getFirebase(), is(notNullValue()));
        assertThat(dto.getFirebase().getRevenue(), is(nullValue()));
        assertThat(dto.getPolygon(), is(notNullValue()));
        assertThat(dto.getPolygon().getRevenue(), is(nullValue()));
        assertThat(dto.getAlphaVantage(), is(notNullValue()));
        assertThat(dto.getAlphaVantage().getRevenue(), is(nullValue()));
        assertThat(dto.getWarnings().size(), is(0));
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
        Assert.getValidationError(
                "/research/" + companyId + "/import/period/25Q2?endingMonth=2025",
                "must match YYYY-MM");
    }

    @Test
    void importEstimate()
    {
        Long companyId = 2281L;
        Long periodId = 2182L;
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

        assertThat(fields.keySet(), containsInAnyOrder("current", "next1", "next2", "next3", "warnings"));
        assertThat(dto.getCurrent().getEps(), is("5"));
        assertThat(dto.getNext1().getEps(), is("6"));
        assertThat(dto.getNext2().getEps(), is("7"));
        assertThat(dto.getNext3().getEps(), is("8"));
        assertThat(dto.getWarnings(), is(List.of()));
    }

    @Test
    void importEstimate_sourceFailureReturnsEmptySuggestion()
    {
        Long companyId = 2281L;
        Long periodId = 2182L;
        when(firebaseService.getLatestEstimate("RCH", "25Q1"))
                .thenThrow(new IllegalStateException("Finnhub unavailable"));

        EstimateImportDto dto = given().when()
                .get("/research/" + companyId + "/import/estimate/" + periodId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().as(EstimateImportDto.class);

        assertThat(dto.getCurrent(), is(nullValue()));
        assertThat(dto.getNext1(), is(nullValue()));
        assertThat(dto.getNext2(), is(nullValue()));
        assertThat(dto.getNext3(), is(nullValue()));
        assertThat(dto.getWarnings(), is(List.of(
                "Firebase/Finnhub estimate 25Q1 for RCH could not be loaded: Finnhub unavailable")));
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
        data.setCapex("-11");
        data.setFreeCashFlow("21");
        data.setAdjustedEps("1.25");
        data.setPriceHigh("151.5");
        data.setPriceLow("81.5");
        return data;
    }
}
