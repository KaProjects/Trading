package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeSellDto;
import org.kaleta.dto.TradesUiDto;
import org.kaleta.framework.Assert;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.service.LatestService;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@QuarkusTest
class TradeEndpointsTest
{
    private final String path = "/trade";

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
        Assert.getValidationError(path + "?companyId=AAAA", "must be a valid UUID");
        Assert.getValidationError(path + "?companyId=", "must be a valid UUID");

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
        TradesUiDto dto = given().when()
                .get("/trade")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(14));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), is("11.11.2023"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), is("05.04.2023"));
        assertThat(dto.getTrades().get(2).getPurchaseDate(), is("01.11.2022"));
        assertThat(dto.getTrades().get(3).getPurchaseDate(), is("10.05.2021"));
        assertThat(dto.getSums(), is(new String[]{"10", "4", "", "", "", "427.62", "2595289.35", "", "", "", "100.5", "10039.5", "2331.17", "30.24"}));
    }

    @Test
    void getTradesFilterActive()
    {
        TradesUiDto dto = given().when()
                .get("/trade?active=true")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);
        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(9));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), is("05.04.2023"));
        assertThat(dto.getTrades().get(0).getTicker(), is("CEZ"));
        assertThat(dto.getTrades().get(0).getPurchaseTotal(), is("575599.35"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), is("01.11.2022"));
        assertThat(dto.getTrades().get(1).getTicker(), is("RR"));
        assertThat(dto.getTrades().get(1).getPurchaseTotal(), is("2000025"));
        assertThat(dto.getSums(), is(new String[]{"7", "3", "", "", "", "346.79", "2588601.02", "", "", "", "0", "0", "", ""}));
    }

    @Test
    void getTradesFilterCurrency()
    {
        TradesUiDto dto = given().when()
                .get("/trade?currency=€")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getTicker(), is("SHELL"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "", "", "18", "2028", "", "", "", "30.5", "3009.5", "981.5", "48.4"}));
    }

    @Test
    void getTradesFilterSector()
    {
        TradesUiDto dto = given().when()
                .get("/trade?sector=" + Sector.SEMICONDUCTORS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getTicker(), is("NVDA"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "", "", "14.5", "2017", "", "", "", "50", "2450", "433", "21.47"}));
    }

    @Test
    void getTradesFilterCompany()
    {
        TradesUiDto dto = given().when()
                .get("/trade?companyId=adb89a0a-86bc-4854-8a55-058ad2e6308f")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getTicker(), is("NVDA"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "", "", "14.5", "2017", "", "", "", "50", "2450", "433", "21.47"}));
    }

    @Test
    void getTradesFilterNonExistentCompany()
    {
        TradesUiDto dto = given().when()
                .get("/trade?companyId=2df6b65f-54fb-4381-9b38-8c25409fe168")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(0));
    }

    @Test
    void getTradesFilterYear()
    {
        TradesUiDto dto = given().when()
                .get("/trade?year=2023")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(3));
        assertThat(dto.getTrades().get(0).getTicker(), is("NVDA"));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), endsWith("2023"));
        assertThat(dto.getTrades().get(1).getTicker(), is("CEZ"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), endsWith("2023"));
        assertThat(dto.getTrades().get(2).getTicker(), is("SHELL"));
        assertThat(dto.getTrades().get(2).getSellDate(), endsWith("2023"));
        assertThat(dto.getSums(), is(new String[]{"3", "3", "", "", "", "282.62", "579644.35", "", "", "", "80.5", "5459.5", "1414.5", "34.97"}));

    }

    @Test
    void getTradesFilterMultiple()
    {
        TradesUiDto dto = given().when()
                .get("/trade?year=2023&companyId=61cc8096-87ac-4197-8b54-7c2595274bcc")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1).getName(), is("#"));
        assertThat(dto.getColumns().get(2).getSubColumns().size(), is(5));
        assertThat(dto.getTrades().size(), is(1));
        assertThat(dto.getTrades().get(0).getTicker(), is("CEZ"));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), endsWith("2023"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "", "", "250.12", "575599.35", "", "", "", "0", "0", "", ""}));
    }

    @Test
    void getTradesZeroTotals()
    {
        TradesUiDto dtoZeroPurchase = given().when()
                .get("/trade?companyId=e7c49260-53da-42c1-80cf-eccf6ed928a7")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(dtoZeroPurchase.getTrades().size(), is(1));
        assertThat(dtoZeroPurchase.getTrades().get(0).getTicker(), is("XXX"));
        assertThat(dtoZeroPurchase.getTrades().get(0).getPurchaseTotal(), is("0"));
        assertThat(dtoZeroPurchase.getSums(), is(new String[]{"1", "1", "", "", "", "0", "0", "", "", "", "5", "95", "", ""}));

        TradesUiDto dtoZeroSell = given().when()
                .get("/trade?companyId=0a16ba1d-99de-4306-8fc5-81ee11b60ea0")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(dtoZeroSell.getTrades().size(), is(1));
        assertThat(dtoZeroSell.getTrades().get(0).getTicker(), is("YYY"));
        assertThat(dtoZeroSell.getTrades().get(0).getSellTotal(), is("0"));
        assertThat(dtoZeroSell.getSums(), is(new String[]{"1", "1", "", "", "", "50", "150", "", "", "", "0", "0", "-150", "-100"}));
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
        assertThat(trade.getTicker(), is("CRE"));
        assertThat(trade.getCurrency(), is(Currency.$));
        assertThat(trade.getPurchaseDate(), is(Date.valueOf(dto.getDate())));
        assertBigDecimals(trade.getPurchasePrice(), new BigDecimal(dto.getPrice()));
        assertBigDecimals(trade.getQuantity(), new BigDecimal(dto.getQuantity()));
        assertBigDecimals(trade.getPurchaseFees(), new BigDecimal(dto.getFees()));
        assertBigDecimals(trade.getPurchaseTotal(), new BigDecimal("1020"));
        assertThat(trade.getSellDate(), is(nullValue()));
        assertThat(trade.getSellPrice(), is(nullValue()));
        assertThat(trade.getSellFees(), is(nullValue()));
        assertThat(trade.getSellTotal(), is(nullValue()));
        assertThat(trade.getProfit(), is(nullValue()));
        assertThat(trade.getProfitPercentage(), is(nullValue()));
    }

    @Test
    void createTrade_invalidParameters()
    {
        String validCompanyId = "21322ef8-9e26-4eda-bf74-b0f0eb8925b1";
        String validDate = "2020-01-01";
        String validPrice = "100.5";
        String validQuantity = "10";
        String validFees = "15";

        Assert.postValidationError(path, null, "must not be null");

        TradeCreateDto dto = new TradeCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setDate(validDate);
        dto.setPrice(validPrice);
        dto.setQuantity(validQuantity);

        dto.setFees(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setFees("");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees("1234");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees("1.123");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees(validFees);

        dto.setPrice(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setPrice("");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("1234567");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("10.12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice(validPrice);

        dto.setQuantity(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setQuantity("");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setQuantity("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setQuantity("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setQuantity(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setQuantity("12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setQuantity("1.12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setQuantity(validQuantity);

        dto.setDate(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setDate("");
        Assert.postValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setDate("01.01.2020");
        Assert.postValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setDate("2020-1-1");
        Assert.postValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setDate(validDate);

        dto.setCompanyId(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setCompanyId("x");
        Assert.postValidationError(path, dto, "must be a valid UUID");

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

        assertThat(trades.get(0).getTicker(), is("SELL"));
        assertBigDecimals(trades.get(0).getQuantity(), new BigDecimal("1"));
        assertThat(trades.get(0).getPurchaseDate(), is(Date.valueOf("2020-03-15")));
        assertBigDecimals(trades.get(0).getPurchasePrice(), new BigDecimal("400"));
        assertBigDecimals(trades.get(0).getPurchaseFees(), new BigDecimal("10"));
        assertThat(trades.get(0).getSellDate(), is(nullValue()));
        assertThat(trades.get(0).getSellPrice(), is(nullValue()));
        assertThat(trades.get(0).getSellFees(), is(nullValue()));

        assertThat(trades.get(1).getId(), is("91d9253e-aee5-4d86-9c3e-18102bff698d"));
        assertThat(trades.get(1).getTicker(), is("SELL"));
        assertBigDecimals(trades.get(1).getQuantity(), new BigDecimal("5"));
        assertThat(trades.get(1).getPurchaseDate(), is(Date.valueOf("2020-04-05")));
        assertBigDecimals(trades.get(1).getPurchasePrice(), new BigDecimal("450"));
        assertBigDecimals(trades.get(1).getPurchaseFees(), new BigDecimal("10"));
        assertThat(trades.get(1).getSellDate(), is(Date.valueOf("2020-07-15")));
        assertBigDecimals(trades.get(1).getSellPrice(), new BigDecimal(dto.getPrice()));
        assertBigDecimals(trades.get(1).getSellFees(), new BigDecimal("10"));

        assertThat(trades.get(2).getId(), is("19993bde-6d06-4006-918f-77baa8062e42"));
        assertThat(trades.get(2).getTicker(), is("SELL"));
        assertBigDecimals(trades.get(2).getQuantity(), new BigDecimal("2.5"));
        assertThat(trades.get(2).getPurchaseDate(), is(Date.valueOf("2020-05-01")));
        assertBigDecimals(trades.get(2).getPurchasePrice(), new BigDecimal("500"));
        assertBigDecimals(trades.get(2).getPurchaseFees(), new BigDecimal("3.33"));
        assertThat(trades.get(2).getSellDate(), is(Date.valueOf("2020-07-15")));
        assertBigDecimals(trades.get(2).getSellPrice(), new BigDecimal(dto.getPrice()));
        assertBigDecimals(trades.get(2).getSellFees(), new BigDecimal("5"));

        assertThat(trades.get(3).getTicker(), is("SELL"));
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

        Assert.putValidationError(path, null, "must not be null");

        TradeSellDto dto = new TradeSellDto();
        dto.setCompanyId(companyId);
        dto.setDate(validDate);
        dto.setPrice(validPrice);
        dto.setFees(validFees);

        Assert.putValidationError(path, dto, "size must be between 1 and 2147483647");

        dto.setTrades(List.of(new TradeSellDto.Trade(validTradeId, validQuantity)));

        dto.setDate(null);
        Assert.putValidationError(path, dto, "must not be null");
        dto.setDate("");
        Assert.putValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setDate("01.01.2020");
        Assert.putValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setDate("2020-1-1");
        Assert.putValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setDate(validDate);

        dto.setPrice(null);
        Assert.putValidationError(path, dto, "must not be null");
        dto.setPrice("");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("1234567");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("10.12345");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice(validPrice);

        dto.setFees(null);
        Assert.putValidationError(path, dto, "must not be null");
        dto.setFees("");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees("1234");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees("1.123");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setFees(validFees);

        dto.getTrades().get(0).setQuantity(null);
        Assert.putValidationError(path, dto, "must not be null");
        dto.getTrades().get(0).setQuantity("");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.getTrades().get(0).setQuantity("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.getTrades().get(0).setQuantity("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.getTrades().get(0).setQuantity(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.getTrades().get(0).setQuantity("12345");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.getTrades().get(0).setQuantity("1.12345");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.getTrades().get(0).setQuantity("5.5");
        Assert.put400("/trade", dto, "unable to sell more than owned for tradeId='" + dto.getTrades().get(0).getTradeId() + "'");
        dto.getTrades().get(0).setQuantity(validQuantity);

        dto.getTrades().get(0).setTradeId(null);
        Assert.putValidationError(path, dto, "must not be null");
        dto.getTrades().get(0).setTradeId("x");
        Assert.putValidationError(path, dto, "must be a valid UUID");

        dto.getTrades().get(0).setTradeId(UUID.randomUUID().toString());
        Assert.put400("/trade", dto, "trade with id '" + dto.getTrades().get(0).getTradeId() + "' not found");
        dto.getTrades().get(0).setTradeId(validTradeId);

        dto.setCompanyId(null);
        Assert.putValidationError(path, dto, "must not be null");
        dto.setCompanyId("x");
        Assert.putValidationError(path, dto, "must be a valid UUID");

        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.put400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");

        dto.setCompanyId("6877c555-1234-4af5-99ef-415980484d8c");
        Assert.put400(path, dto, "provided companyId and trade='" + validTradeId + "' companyId doesn't match");
    }
}