package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Assert;
import org.kaleta.model.Trades;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.persistence.entity.Record;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.TradeCreateDto;
import org.kaleta.rest.dto.TradeSellDto;
import org.kaleta.rest.dto.TradeUpdateDto;
import org.kaleta.service.LatestService;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_3_2_false;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_4_4_false;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_6_4_false;
import static org.kaleta.framework.Assert.ExpectedViolation.MATCH_DATE_FORMAT;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@QuarkusTest
class TradeEndpointsTest
{
    String path = "/trade";

    @Inject
    TradeDao tradeDao;
    @Inject
    RecordDao recordDao;

    @InjectMock
    LatestService latestService;

    @BeforeEach
    void before()
    {
        when(latestService.getSyncedForWithWarnings(any()))
                .thenReturn(new LatestService.SyncResult(null, List.of()));
    }

    @Test
    void getTrades_invalidParameters()
    {
        Assert.getValidationError(path + "?companyId=0", VALID_ID);

        Assert.getValidationError(path + "?currency=X", "must be any of Currency");

        Assert.getValidationError(path + "?year=20x2", "must match YYYY");
        Assert.getValidationError(path + "?year=20222", "must match YYYY");
        Assert.getValidationError(path + "?year=202", "must match YYYY");

        Assert.getValidationError(path + "?sector=X", "must be any of Sector");

        Assert.getValidationError(path + "?portfolio=X", "must be any of Portfolio");
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
        assertThat(dto.getTrades().get(0).isActive(), is(false));
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
        assertThat(dto.getTrades().get(0).isActive(), is(true));
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
        when(latestService.getSyncedForWithWarnings(any()))
                .thenReturn(new LatestService.SyncResult(latest, List.of()));

        Trades dto = given().when()
                .get("/trade?active=true")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dto.getTrades().get(0).getSellDate().toString(), is("2026-05-09"));
        assertThat(dto.getTrades().get(0).isActive(), is(true));
        assertBigDecimals(dto.getTrades().get(0).getSellPrice(), new BigDecimal("321.45"));
        assertBigDecimals(dto.getTrades().get(0).getSellFees(), dto.getTrades().get(0).getPurchaseFees());
        assertBigDecimals(dto.getTrades().get(0).getSellTotal(), new BigDecimal("369457.06"));
        assertBigDecimals(dto.getTrades().get(0).getProfit(), new BigDecimal("-206142.3"));
        assertBigDecimals(dto.getTrades().get(0).getProfitPercentage(), new BigDecimal("-35.8100"));
    }

    @Test
    void getTradesFilterActive_externalFailureReturnsWarning()
    {
        when(latestService.getSyncedForWithWarnings(any()))
                .thenReturn(new LatestService.SyncResult(
                        null,
                        List.of("Finnhub quote could not be loaded: daily limit exceeded")));

        Trades dto = given().when()
                .get("/trade?active=true")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().as(Trades.class);

        assertThat(dto.getWarnings(), is(List.of(
                "Finnhub quote could not be loaded: daily limit exceeded")));
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
    void getTradesFilterPortfolio()
    {
        Trades dto = given().when()
                .get("/trade?portfolio=" + Portfolio.PATRIA_MARGIN)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", Trades.class);

        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getCompany().getTicker(), is("NVDA"));
        assertThat(dto.getTrades().get(0).getPortfolio().getKey(), is(Portfolio.PATRIA_MARGIN.name()));
        assertThat(dto.getAggregates().getCompanies(), is(1));
        assertThat(dto.getAggregates().getCurrencies(), is(1));
        assertThat(dto.getAggregates().getPortfolios(), is(1));
    }

    @Test
    void getTradesFilterCompany()
    {
        Trades dto = given().when()
                .get("/trade?companyId=1927")
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
                .get("/trade?companyId=4294967295")
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
                .get("/trade?year=2023&companyId=1531")
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
                .get("/trade?companyId=2213")
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
                .get("/trade?companyId=1041")
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
        dto.setCompanyId(1565L);
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
        assertThat(trade.getPortfolio(), is(nullValue()));
        assertThat(trade.getSellDate(), is(nullValue()));
        assertThat(trade.getSellPrice(), is(nullValue()));
        assertThat(trade.getSellFees(), is(nullValue()));

        List<Record> records = recordDao.list(dto.getCompanyId());
        assertThat(records.size(), is(1));
        assertThat(records.get(0).getStrategy(), is(
                "[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\"bought 10@100.5$\"}]}]}]"));
    }

    @Test
    void createTrade_invalidParameters()
    {
        Long validCompanyId = 1173L;
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
        dto.setPortfolio(Portfolio.REVOLUT_STANDARD.toString());

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

        dto.setPortfolio("INVALID");
        Assert.postValidationError(path, dto, "must be any of Portfolio");
        dto.setPortfolio(Portfolio.REVOLUT_STANDARD.toString());

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
        dto.setCompanyId(0L);
        Assert.postValidationError(path, dto, VALID_ID);

        dto.setCompanyId(4_294_967_295L);
        Assert.post400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");
    }

    @Test
    void sellTrade()
    {
        TradeSellDto dto = new TradeSellDto();
        dto.setCompanyId(1209L);
        dto.setDate("2020-07-15");
        dto.setPrice("600");
        dto.setFees("15");
        dto.getTrades().add(new TradeSellDto.Trade(1788L, "5"));
        dto.getTrades().add(new TradeSellDto.Trade(1130L, "2.5"));

        Assert.put204(path, dto);

        List<Trade> trades = tradeDao.list(1209L);
        trades.sort(Comparator.comparing(Trade::getPurchaseDate).thenComparing(Trade::getId));
        assertThat(trades.size(), is(4));

        assertThat(trades.get(0).getCompany().getTicker(), is("SELL"));
        assertBigDecimals(trades.get(0).getQuantity(), new BigDecimal("1"));
        assertThat(trades.get(0).getPurchaseDate(), is(Date.valueOf("2020-03-15")));
        assertBigDecimals(trades.get(0).getPurchasePrice(), new BigDecimal("400"));
        assertBigDecimals(trades.get(0).getPurchaseFees(), new BigDecimal("10"));
        assertThat(trades.get(0).getSellDate(), is(nullValue()));
        assertThat(trades.get(0).getSellPrice(), is(nullValue()));
        assertThat(trades.get(0).getSellFees(), is(nullValue()));

        assertThat(trades.get(1).getId(), is(1788L));
        assertThat(trades.get(1).getCompany().getTicker(), is("SELL"));
        assertBigDecimals(trades.get(1).getQuantity(), new BigDecimal("5"));
        assertThat(trades.get(1).getPurchaseDate(), is(Date.valueOf("2020-04-05")));
        assertBigDecimals(trades.get(1).getPurchasePrice(), new BigDecimal("450"));
        assertBigDecimals(trades.get(1).getPurchaseFees(), new BigDecimal("10"));
        assertThat(trades.get(1).getSellDate(), is(Date.valueOf("2020-07-15")));
        assertBigDecimals(trades.get(1).getSellPrice(), new BigDecimal(dto.getPrice()));
        assertBigDecimals(trades.get(1).getSellFees(), new BigDecimal("10"));

        assertThat(trades.get(2).getId(), is(1130L));
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

        List<Record> records = recordDao.list(dto.getCompanyId());
        assertThat(records.size(), is(1));
        assertThat(records.get(0).getStrategy(), is(
                "[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\"sold 7.5@600$\"},{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\"- 7.5@466.66667$ - 28.33$ = +971.67$ (+27.66%)\"}]}]}]}]}]"));
    }

    @Test
    void sellTrade_invalidParameters()
    {
        Long companyId = 1209L;
        String validDate = "2020-01-01";
        String validPrice = "100.5";
        String validFees = "15";
        Long validTradeId = 1788L;
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
        dto.getTrades().get(0).setTradeId(0L);
        Assert.putValidationError(path, dto, VALID_ID);

        dto.getTrades().get(0).setTradeId(4_294_967_295L);
        Assert.put400("/trade", dto, "trade with id '" + dto.getTrades().get(0).getTradeId() + "' not found");
        dto.getTrades().get(0).setTradeId(validTradeId);

        dto.setCompanyId(null);
        Assert.putValidationError(path, dto, NOT_NULL);
        dto.setCompanyId(0L);
        Assert.putValidationError(path, dto, VALID_ID);

        dto.setCompanyId(4_294_967_295L);
        Assert.put400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");

        dto.setCompanyId(1565L);
        Assert.put400(path, dto, "provided companyId and trade='" + validTradeId + "' companyId doesn't match");
    }

    @Test
    void updateTrade()
    {
        Trades.Trade original = given().when()
                .get(path + "?companyId=1927")
                .then()
                .statusCode(200)
                .extract().as(Trades.class)
                .getTrades().get(0);

        try {
            TradeUpdateDto dto = validTradeUpdateDto();
            dto.setPortfolio(Portfolio.REVOLUT_STANDARD.toString());

            Assert.put204(path + "/1", dto);

            Trades.Trade updated = given().when()
                    .get(path + "?companyId=1927")
                    .then()
                    .statusCode(200)
                    .extract().as(Trades.class)
                    .getTrades().get(0);
            assertThat(updated.getCompany().getId(), is(1927L));
            assertThat(updated.getPurchaseDate().toString(), is(dto.getPurchaseDate()));
            assertBigDecimals(updated.getPurchaseQuantity(), new BigDecimal(dto.getQuantity()));
            assertBigDecimals(updated.getPurchasePrice(), new BigDecimal(dto.getPurchasePrice()));
            assertBigDecimals(updated.getPurchaseFees(), new BigDecimal(dto.getPurchaseFees()));
            assertThat(updated.getPortfolio().getKey(), is(Portfolio.REVOLUT_STANDARD.toString()));
            assertThat(updated.getSellDate().toString(), is(dto.getSellDate()));
            assertBigDecimals(updated.getSellPrice(), new BigDecimal(dto.getSellPrice()));
            assertBigDecimals(updated.getSellFees(), new BigDecimal(dto.getSellFees()));
        } finally {
            TradeUpdateDto restore = new TradeUpdateDto();
            restore.setPurchaseDate(original.getPurchaseDate().toString());
            restore.setQuantity(original.getPurchaseQuantity().toPlainString());
            restore.setPurchasePrice(original.getPurchasePrice().toPlainString());
            restore.setPurchaseFees(original.getPurchaseFees().toPlainString());
            restore.setPortfolio(original.getPortfolio() == null ? null : original.getPortfolio().getKey());
            restore.setSellDate(original.getSellDate().toString());
            restore.setSellPrice(original.getSellPrice().toPlainString());
            restore.setSellFees(original.getSellFees().toPlainString());
            Assert.put204(path + "/1", restore);
        }
    }

    @Test
    void updateTrade_invalidParameters()
    {
        TradeUpdateDto dto = validTradeUpdateDto();

        Assert.putValidationError(path + "/1", null, NOT_NULL);
        Assert.putValidationError(path + "/0", dto, VALID_ID);

        dto.setPurchaseDate(null);
        Assert.putValidationError(path + "/1", dto, NOT_NULL);
        dto.setPurchaseDate("");
        Assert.putValidationError(path + "/1", dto, MATCH_DATE_FORMAT);
        dto.setPurchaseDate("2024-02-01");

        dto.setQuantity(null);
        Assert.putValidationError(path + "/1", dto, NOT_NULL);
        dto.setQuantity("12345");
        Assert.putValidationError(path + "/1", dto, BIG_DECIMAL_4_4_false);
        dto.setQuantity("6.25");

        dto.setPurchasePrice(null);
        Assert.putValidationError(path + "/1", dto, NOT_NULL);
        dto.setPurchasePrice("1234567");
        Assert.putValidationError(path + "/1", dto, BIG_DECIMAL_6_4_false);
        dto.setPurchasePrice("410.25");

        dto.setPurchaseFees(null);
        Assert.putValidationError(path + "/1", dto, NOT_NULL);
        dto.setPurchaseFees("1234");
        Assert.putValidationError(path + "/1", dto, BIG_DECIMAL_3_2_false);
        dto.setPurchaseFees("15.25");

        dto.setPortfolio("INVALID");
        Assert.putValidationError(path + "/1", dto, "must be any of Portfolio");
        dto.setPortfolio(Portfolio.PATRIA_MARGIN.toString());

        dto.setSellDate("");
        Assert.putValidationError(path + "/1", dto, MATCH_DATE_FORMAT);
        dto.setSellDate("2024-03-01");

        dto.setSellPrice("");
        Assert.putValidationError(path + "/1", dto, BIG_DECIMAL_6_4_false);
        dto.setSellPrice("510.75");

        dto.setSellFees("");
        Assert.putValidationError(path + "/1", dto, BIG_DECIMAL_3_2_false);
        dto.setSellFees("16.5");

        Assert.put400(path + "/4294967295", dto, "trade with id '4294967295' not found");
        Assert.put400(path + "/3", dto, "sale fields cannot be provided for active trade");

        dto.setSellDate(null);
        dto.setSellPrice(null);
        dto.setSellFees(null);
        Assert.put400(path + "/1", dto, "sellDate, sellPrice and sellFees are required for sold trade");

        dto.setSellDate("2024-01-01");
        dto.setSellPrice("510.75");
        dto.setSellFees("16.5");
        Assert.put400(path + "/1", dto, "sellDate cannot be before purchaseDate");
    }

    private TradeUpdateDto validTradeUpdateDto()
    {
        TradeUpdateDto dto = new TradeUpdateDto();
        dto.setPurchaseDate("2024-02-01");
        dto.setQuantity("6.25");
        dto.setPurchasePrice("410.25");
        dto.setPurchaseFees("15.25");
        dto.setPortfolio(Portfolio.PATRIA_MARGIN.toString());
        dto.setSellDate("2024-03-01");
        dto.setSellPrice("510.75");
        dto.setSellFees("16.5");
        return dto;
    }
}
