package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Assert;
import org.kaleta.model.Trades;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.TradeCreateDto;
import org.kaleta.rest.dto.TradeSellDto;
import org.kaleta.service.LatestService;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_3_2_false;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_4_4_false;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_6_4_false;
import static org.kaleta.framework.Assert.ExpectedViolation.MATCH_DATE_FORMAT;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_UUID;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@QuarkusTest
class TradeEndpointsTest
{
    String path = "/trade";

    @Inject
    TradeDao tradeDao;

    @InjectMock
    LatestService latestService;

    @BeforeEach
    void before()
    {
        when(latestService.getSyncedFor(any())).thenReturn(null);
    }

    @Test
    void getTrades_invalidParameters()
    {
        Assert.getValidationError(path + "?companyId=AAAA", VALID_UUID);
        Assert.getValidationError(path + "?companyId=", VALID_UUID);

        Assert.getValidationError(path + "?currency=X", "must be any of Currency");
        Assert.getValidationError(path + "?currency=", "must be any of Currency");

        Assert.getValidationError(path + "?year=20x2", "must match YYYY");
        Assert.getValidationError(path + "?year=20222", "must match YYYY");
        Assert.getValidationError(path + "?year=202", "must match YYYY");
        Assert.getValidationError(path + "?year=", "must match YYYY");

        Assert.getValidationError(path + "?sector=X", "must be any of Sector");
        Assert.getValidationError(path + "?sector=", "must be any of Sector");
    }

    @Test
    void getTradesFilterNone()
    {
        Trades dto = given().when()
                .get("/trade")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dto.getTrades().size(), is(14));
        assertThat(dto.getTrades().get(0).getPurchaseDate().toString(), is("2023-11-11"));
        assertThat(dto.getTrades().get(1).getPurchaseDate().toString(), is("2023-04-05"));
        assertThat(dto.getTrades().get(2).getPurchaseDate().toString(), is("2022-11-01"));
        assertThat(dto.getTrades().get(3).getPurchaseDate().toString(), is("2021-05-10"));

        assertThat(dto.getAggregates().getCompanies(), is(10));
        assertThat(dto.getAggregates().getCurrencies(), is(4));
        assertBigDecimals(dto.getAggregates().getPurchaseFees(), new BigDecimal("427.62"));
        assertBigDecimals(dto.getAggregates().getPurchaseTotal(), new BigDecimal("2595289.2"));
        assertBigDecimals(dto.getAggregates().getSellFees(), new BigDecimal("100.50"));
        assertBigDecimals(dto.getAggregates().getSellTotal(), new BigDecimal("10039.50"));
        assertBigDecimals(dto.getAggregates().getProfit(), new BigDecimal("2331.17"));
        assertBigDecimals(dto.getAggregates().getProfitPercentage(), new BigDecimal("30.24"));
    }

    @Test
    void getTradesFilterActive()
    {
        Trades dto = given().when()
                .get("/trade?active=true")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);
        assertThat(dto.getTrades().size(), is(9));
        assertThat(dto.getTrades().get(0).getPurchaseDate().toString(), is("2023-04-05"));
        assertThat(dto.getTrades().get(0).getCompany().getTicker(), is("CEZ"));
        assertBigDecimals(dto.getTrades().get(0).getPurchaseTotal(), new BigDecimal("575599.4"));
        assertThat(dto.getTrades().get(1).getPurchaseDate().toString(), is("2022-11-01"));
        assertThat(dto.getTrades().get(1).getCompany().getTicker(), is("RR"));
        assertBigDecimals(dto.getTrades().get(1).getPurchaseTotal(), new BigDecimal("2000025.00"));

        assertThat(dto.getAggregates().getCompanies(), is(7));
        assertThat(dto.getAggregates().getCurrencies(), is(3));
        assertBigDecimals(dto.getAggregates().getPurchaseFees(), new BigDecimal("346.79"));
        assertBigDecimals(dto.getAggregates().getPurchaseTotal(), new BigDecimal("2588601.0"));
        assertBigDecimals(dto.getAggregates().getSellFees(), new BigDecimal("0.00"));
        assertBigDecimals(dto.getAggregates().getSellTotal(), new BigDecimal("0.00"));
        assertThat(dto.getAggregates().getProfit(), is(nullValue()));
        assertThat(dto.getAggregates().getProfitPercentage(), is(nullValue()));
    }

    @Test
    void getTradesFilterActive_syncsLatestValues()
    {
        Latest latest = new Latest();
        latest.setDatetime(LocalDateTime.of(2026, 5, 9, 14, 35));
        latest.setPrice(new BigDecimal("321.45"));
        when(latestService.getSyncedFor(any())).thenReturn(latest);

        Trades dto = given().when()
                .get("/trade?active=true")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dto.getTrades().get(0).getSellDate().toString(), is("2026-05-09"));
        assertBigDecimals(dto.getTrades().get(0).getSellPrice(), new BigDecimal("321.45"));
        assertBigDecimals(dto.getTrades().get(0).getSellFees(), dto.getTrades().get(0).getPurchaseFees());
    }

    @Test
    void getTradesFilterCurrency()
    {
        Trades dto = given().when()
                .get("/trade?currency=€")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getCompany().getTicker(), is("SHELL"));
        assertThat(dto.getAggregates().getCompanies(), is(1));
        assertThat(dto.getAggregates().getCurrencies(), is(1));
        assertBigDecimals(dto.getAggregates().getPurchaseFees(), new BigDecimal("18.00"));
        assertBigDecimals(dto.getAggregates().getPurchaseTotal(), new BigDecimal("2028.00"));
        assertBigDecimals(dto.getAggregates().getSellFees(), new BigDecimal("30.50"));
        assertBigDecimals(dto.getAggregates().getSellTotal(), new BigDecimal("3009.50"));
        assertBigDecimals(dto.getAggregates().getProfit(), new BigDecimal("981.50"));
        assertBigDecimals(dto.getAggregates().getProfitPercentage(), new BigDecimal("48.40"));
    }

    @Test
    void getTradesFilterSector()
    {
        Trades dto = given().when()
                .get("/trade?sector=" + Sector.SEMICONDUCTORS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getCompany().getTicker(), is("NVDA"));
        assertThat(dto.getAggregates().getCompanies(), is(1));
        assertThat(dto.getAggregates().getCurrencies(), is(1));
        assertBigDecimals(dto.getAggregates().getPurchaseFees(), new BigDecimal("14.50"));
        assertBigDecimals(dto.getAggregates().getPurchaseTotal(), new BigDecimal("2017.00"));
        assertBigDecimals(dto.getAggregates().getSellFees(), new BigDecimal("50.00"));
        assertBigDecimals(dto.getAggregates().getSellTotal(), new BigDecimal("2450.00"));
        assertBigDecimals(dto.getAggregates().getProfit(), new BigDecimal("433.00"));
        assertBigDecimals(dto.getAggregates().getProfitPercentage(), new BigDecimal("21.47"));
    }

    @Test
    void getTradesFilterCompany()
    {
        Trades dto = given().when()
                .get("/trade?companyId=adb89a0a-86bc-4854-8a55-058ad2e6308f")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getCompany().getTicker(), is("NVDA"));
        assertThat(dto.getAggregates().getCompanies(), is(1));
        assertThat(dto.getAggregates().getCurrencies(), is(1));
        assertBigDecimals(dto.getAggregates().getPurchaseFees(), new BigDecimal("14.50"));
        assertBigDecimals(dto.getAggregates().getPurchaseTotal(), new BigDecimal("2017.00"));
        assertBigDecimals(dto.getAggregates().getSellFees(), new BigDecimal("50.00"));
        assertBigDecimals(dto.getAggregates().getSellTotal(), new BigDecimal("2450.00"));
        assertBigDecimals(dto.getAggregates().getProfit(), new BigDecimal("433.00"));
        assertBigDecimals(dto.getAggregates().getProfitPercentage(), new BigDecimal("21.47"));
    }

    @Test
    void getTradesFilterNonExistentCompany()
    {
        Trades dto = given().when()
                .get("/trade?companyId=2df6b65f-54fb-4381-9b38-8c25409fe168")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dto.getTrades().size(), is(0));
        assertThat(dto.getAggregates().getCompanies(), is(0));
        assertThat(dto.getAggregates().getCurrencies(), is(0));
        assertBigDecimals(dto.getAggregates().getPurchaseFees(), new BigDecimal("0.00"));
        assertBigDecimals(dto.getAggregates().getPurchaseTotal(), new BigDecimal("0.00"));
        assertBigDecimals(dto.getAggregates().getSellFees(), new BigDecimal("0.00"));
        assertBigDecimals(dto.getAggregates().getSellTotal(), new BigDecimal("0.00"));
        assertThat(dto.getAggregates().getProfit(), is(nullValue()));
        assertThat(dto.getAggregates().getProfitPercentage(), is(nullValue()));
    }

    @Test
    void getTradesFilterYear()
    {
        Trades dto = given().when()
                .get("/trade?year=2023")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dto.getTrades().size(), is(3));
        assertThat(dto.getTrades().get(0).getCompany().getTicker(), is("NVDA"));
        assertThat(dto.getTrades().get(0).getPurchaseDate().toString(), is("2023-11-11"));
        assertThat(dto.getTrades().get(1).getCompany().getTicker(), is("CEZ"));
        assertThat(dto.getTrades().get(1).getPurchaseDate().toString(), is("2023-04-05"));
        assertThat(dto.getTrades().get(2).getCompany().getTicker(), is("SHELL"));
        assertThat(dto.getTrades().get(2).getSellDate().toString(), is("2023-12-31"));
        assertThat(dto.getAggregates().getCompanies(), is(3));
        assertThat(dto.getAggregates().getCurrencies(), is(3));
        assertBigDecimals(dto.getAggregates().getPurchaseFees(), new BigDecimal("282.62"));
        assertBigDecimals(dto.getAggregates().getPurchaseTotal(), new BigDecimal("579644.4"));
        assertBigDecimals(dto.getAggregates().getSellFees(), new BigDecimal("80.50"));
        assertBigDecimals(dto.getAggregates().getSellTotal(), new BigDecimal("5459.50"));
        assertBigDecimals(dto.getAggregates().getProfit(), new BigDecimal("1414.50"));
        assertBigDecimals(dto.getAggregates().getProfitPercentage(), new BigDecimal("34.97"));

    }

    @Test
    void getTradesFilterMultiple()
    {
        Trades dto = given().when()
                .get("/trade?year=2023&companyId=61cc8096-87ac-4197-8b54-7c2595274bcc")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getCompany().getTicker(), is("CEZ"));
        assertThat(dto.getTrades().get(0).getPurchaseDate().toString(), is("2023-04-05"));
        assertThat(dto.getAggregates().getCompanies(), is(1));
        assertThat(dto.getAggregates().getCurrencies(), is(1));
        assertBigDecimals(dto.getAggregates().getPurchaseFees(), new BigDecimal("250.12"));
        assertBigDecimals(dto.getAggregates().getPurchaseTotal(), new BigDecimal("575599.4"));
        assertBigDecimals(dto.getAggregates().getSellFees(), new BigDecimal("0.00"));
        assertBigDecimals(dto.getAggregates().getSellTotal(), new BigDecimal("0.00"));
        assertThat(dto.getAggregates().getProfit(), is(nullValue()));
        assertThat(dto.getAggregates().getProfitPercentage(), is(nullValue()));
    }

    @Test
    void getTradesZeroTotals()
    {
        Trades dtoZeroPurchase = given().when()
                .get("/trade?companyId=e7c49260-53da-42c1-80cf-eccf6ed928a7")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dtoZeroPurchase.getTrades().size(), is(1));
        assertThat(dtoZeroPurchase.getTrades().get(0).getCompany().getTicker(), is("XXX"));
        assertBigDecimals(dtoZeroPurchase.getTrades().get(0).getPurchaseTotal(), new BigDecimal("0.00"));
        assertThat(dtoZeroPurchase.getAggregates().getCompanies(), is(1));
        assertThat(dtoZeroPurchase.getAggregates().getCurrencies(), is(1));
        assertBigDecimals(dtoZeroPurchase.getAggregates().getPurchaseFees(), new BigDecimal("0.00"));
        assertBigDecimals(dtoZeroPurchase.getAggregates().getPurchaseTotal(), new BigDecimal("0.00"));
        assertBigDecimals(dtoZeroPurchase.getAggregates().getSellFees(), new BigDecimal("5.00"));
        assertBigDecimals(dtoZeroPurchase.getAggregates().getSellTotal(), new BigDecimal("95.00"));
        assertThat(dtoZeroPurchase.getAggregates().getProfit(), is(nullValue()));
        assertThat(dtoZeroPurchase.getAggregates().getProfitPercentage(), is(nullValue()));

        Trades dtoZeroSell = given().when()
                .get("/trade?companyId=0a16ba1d-99de-4306-8fc5-81ee11b60ea0")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dtoZeroSell.getTrades().size(), is(1));
        assertThat(dtoZeroSell.getTrades().get(0).getCompany().getTicker(), is("YYY"));
        assertBigDecimals(dtoZeroSell.getTrades().get(0).getSellTotal(), new BigDecimal("0.00"));
        assertThat(dtoZeroSell.getAggregates().getCompanies(), is(1));
        assertThat(dtoZeroSell.getAggregates().getCurrencies(), is(1));
        assertBigDecimals(dtoZeroSell.getAggregates().getPurchaseFees(), new BigDecimal("50.00"));
        assertBigDecimals(dtoZeroSell.getAggregates().getPurchaseTotal(), new BigDecimal("150.00"));
        assertBigDecimals(dtoZeroSell.getAggregates().getSellFees(), new BigDecimal("0.00"));
        assertBigDecimals(dtoZeroSell.getAggregates().getSellTotal(), new BigDecimal("0.00"));
        assertBigDecimals(dtoZeroSell.getAggregates().getProfit(), new BigDecimal("-150.00"));
        assertBigDecimals(dtoZeroSell.getAggregates().getProfitPercentage(), new BigDecimal("-100.00"));
    }

    @Test
    void createTrade()
    {
        TradeCreateDto dto = new TradeCreateDto();
        dto.setCompanyId("6877c555-1234-4af5-99ef-415980484d8c");
        dto.setDate("2020-01-01");
        dto.setPrice("100.5");
        dto.setQuantity("10");
        dto.setFees("15");

        Assert.post201(path, dto);

        List<Trade> trades = tradeDao.list(dto.getCompanyId());
        assertThat(trades.size(), is(1));
        Trade trade = trades.get(0);
        assertThat(trade.getCompany().getTicker(), is("CRE"));
        assertThat(trade.getCompany().getCurrency(), is(Currency.$));
        assertThat(trade.getPurchaseDate(), is(Date.valueOf(dto.getDate())));
        assertBigDecimals(trade.getPurchasePrice(), new BigDecimal(dto.getPrice()));
        assertBigDecimals(trade.getQuantity(), new BigDecimal(dto.getQuantity()));
        assertBigDecimals(trade.getPurchaseFees(), new BigDecimal(dto.getFees()));
        assertThat(trade.getSellDate(), is(nullValue()));
        assertThat(trade.getSellPrice(), is(nullValue()));
        assertThat(trade.getSellFees(), is(nullValue()));
    }

    @Test
    void createTrade_invalidParameters()
    {
        String validCompanyId = "21322ef8-9e26-4eda-bf74-b0f0eb8925b1";
        String validDate = "2020-01-01";
        String validPrice = "100.5";
        String validQuantity = "10";
        String validFees = "15";

        Assert.postValidationError(path, null, NOT_NULL);

        TradeCreateDto dto = new TradeCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setDate(validDate);
        dto.setPrice(validPrice);
        dto.setQuantity(validQuantity);

        dto.setFees(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setFees("");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees("1234");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees("1.123");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees("-1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees(validFees);

        dto.setPrice(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setPrice("");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("1234567");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("10.12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("-1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice(validPrice);

        dto.setQuantity(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setQuantity("");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setQuantity("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setQuantity("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setQuantity(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setQuantity("12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setQuantity("1.12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setQuantity("-1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setQuantity(validQuantity);

        dto.setDate(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setDate("");
        Assert.postValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setDate("01.01.2020");
        Assert.postValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setDate("2020-1-1");
        Assert.postValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setDate(validDate);

        dto.setCompanyId(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setCompanyId("x");
        Assert.postValidationError(path, dto, VALID_UUID);

        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.post400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");
    }

    @Test
    void sellTrade()
    {
        TradeSellDto dto = new TradeSellDto();
        dto.setCompanyId("287d3d0f-4e0c-4b5a-9f8e-2d1c3b0a5f47");
        dto.setDate("2020-07-15");
        dto.setPrice("600");
        dto.setFees("15");
        dto.getTrades().add(new TradeSellDto.Trade("91d9253e-aee5-4d86-9c3e-18102bff698d", "5"));
        dto.getTrades().add(new TradeSellDto.Trade("19993bde-6d06-4006-918f-77baa8062e42", "2.5"));

        Assert.put204(path, dto);

        List<Trade> trades = tradeDao.list("287d3d0f-4e0c-4b5a-9f8e-2d1c3b0a5f47");
        assertThat(trades.size(), is(4));

        assertThat(trades.get(0).getCompany().getTicker(), is("SELL"));
        assertBigDecimals(trades.get(0).getQuantity(), new BigDecimal("1"));
        assertThat(trades.get(0).getPurchaseDate(), is(Date.valueOf("2020-03-15")));
        assertBigDecimals(trades.get(0).getPurchasePrice(), new BigDecimal("400"));
        assertBigDecimals(trades.get(0).getPurchaseFees(), new BigDecimal("10"));
        assertThat(trades.get(0).getSellDate(), is(nullValue()));
        assertThat(trades.get(0).getSellPrice(), is(nullValue()));
        assertThat(trades.get(0).getSellFees(), is(nullValue()));

        assertThat(trades.get(1).getId(), is("91d9253e-aee5-4d86-9c3e-18102bff698d"));
        assertThat(trades.get(1).getCompany().getTicker(), is("SELL"));
        assertBigDecimals(trades.get(1).getQuantity(), new BigDecimal("5"));
        assertThat(trades.get(1).getPurchaseDate(), is(Date.valueOf("2020-04-05")));
        assertBigDecimals(trades.get(1).getPurchasePrice(), new BigDecimal("450"));
        assertBigDecimals(trades.get(1).getPurchaseFees(), new BigDecimal("10"));
        assertThat(trades.get(1).getSellDate(), is(Date.valueOf("2020-07-15")));
        assertBigDecimals(trades.get(1).getSellPrice(), new BigDecimal(dto.getPrice()));
        assertBigDecimals(trades.get(1).getSellFees(), new BigDecimal("10"));

        assertThat(trades.get(2).getId(), is("19993bde-6d06-4006-918f-77baa8062e42"));
        assertThat(trades.get(2).getCompany().getTicker(), is("SELL"));
        assertBigDecimals(trades.get(2).getQuantity(), new BigDecimal("2.5"));
        assertThat(trades.get(2).getPurchaseDate(), is(Date.valueOf("2020-05-01")));
        assertBigDecimals(trades.get(2).getPurchasePrice(), new BigDecimal("500"));
        assertBigDecimals(trades.get(2).getPurchaseFees(), new BigDecimal("3.33"));
        assertThat(trades.get(2).getSellDate(), is(Date.valueOf("2020-07-15")));
        assertBigDecimals(trades.get(2).getSellPrice(), new BigDecimal(dto.getPrice()));
        assertBigDecimals(trades.get(2).getSellFees(), new BigDecimal("5"));

        assertThat(trades.get(3).getCompany().getTicker(), is("SELL"));
        assertBigDecimals(trades.get(3).getQuantity(), new BigDecimal("5"));
        assertThat(trades.get(3).getPurchaseDate(), is(Date.valueOf("2020-05-01")));
        assertBigDecimals(trades.get(3).getPurchasePrice(), new BigDecimal("500"));
        assertBigDecimals(trades.get(3).getPurchaseFees(), new BigDecimal("6.67"));
        assertThat(trades.get(3).getSellDate(), is(nullValue()));
        assertThat(trades.get(3).getSellPrice(), is(nullValue()));
        assertThat(trades.get(3).getSellFees(), is(nullValue()));
    }

    @Test
    void sellTrade_invalidParameters()
    {
        String companyId = "287d3d0f-4e0c-4b5a-9f8e-2d1c3b0a5f47";
        String validDate = "2020-01-01";
        String validPrice = "100.5";
        String validFees = "15";
        String validTradeId = "91d9253e-aee5-4d86-9c3e-18102bff698d";
        String validQuantity = "8";

        Assert.putValidationError(path, null, NOT_NULL);

        TradeSellDto dto = new TradeSellDto();
        dto.setCompanyId(companyId);
        dto.setDate(validDate);
        dto.setPrice(validPrice);
        dto.setFees(validFees);

        Assert.putValidationError(path, dto, "size must be between 1 and 2147483647");

        dto.setTrades(List.of(new TradeSellDto.Trade(validTradeId, validQuantity)));

        dto.setDate(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.setDate("");
        Assert.putValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setDate("01.01.2020");
        Assert.putValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setDate("2020-1-1");
        Assert.putValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setDate(validDate);

        dto.setPrice(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.setPrice("");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("1234567");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("10.12345");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("-1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice(validPrice);

        dto.setFees(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.setFees("");
        Assert.putValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees("1234");
        Assert.putValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees("1.123");
        Assert.putValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setFees(validFees);

        dto.getTrades().get(0).setQuantity(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.getTrades().get(0).setQuantity("");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.getTrades().get(0).setQuantity("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.getTrades().get(0).setQuantity("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.getTrades().get(0).setQuantity(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.getTrades().get(0).setQuantity("12345");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.getTrades().get(0).setQuantity("1.12345");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.getTrades().get(0).setQuantity("-1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.getTrades().get(0).setQuantity("5.5");
        Assert.put400("/trade", dto, "unable to sell more than owned for tradeId='" + dto.getTrades().get(0).getTradeId() + "'");
        dto.getTrades().get(0).setQuantity(validQuantity);

        dto.getTrades().get(0).setTradeId(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.getTrades().get(0).setTradeId("x");
        Assert.putValidationError(path, dto, VALID_UUID);

        dto.getTrades().get(0).setTradeId(UUID.randomUUID().toString());
        Assert.put400("/trade", dto, "trade with id '" + dto.getTrades().get(0).getTradeId() + "' not found");
        dto.getTrades().get(0).setTradeId(validTradeId);

        dto.setCompanyId(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.setCompanyId("x");
        Assert.putValidationError(path, dto, VALID_UUID);

        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.put400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");

        dto.setCompanyId("6877c555-1234-4af5-99ef-415980484d8c");
        Assert.put400(path, dto, "provided companyId and trade='" + validTradeId + "' companyId doesn't match");
    }
}
