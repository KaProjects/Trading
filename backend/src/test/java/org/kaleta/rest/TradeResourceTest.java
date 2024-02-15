package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeDto;
import org.kaleta.dto.TradeSellDto;
import org.kaleta.dto.TradesUiDto;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Sector;
import org.kaleta.framework.Assert;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;


@QuarkusTest
class TradeResourceTest
{
    @Test
    void parameterValidator()
    {
        Assert.get400("/trade?companyId=" + "AAAAAA", "Invalid UUID Parameter");
        Assert.get400("/trade?companyId=", "Invalid UUID Parameter");

        Assert.get400("/trade?currency=" + "X", "Invalid Currency Parameter");
        Assert.get400("/trade?currency=", "Invalid Currency Parameter");

        Assert.get400("/trade?year=" + "20x2", "Invalid Year Parameter");
        Assert.get400("/trade?year=" + "20222", "Invalid Year Parameter");
        Assert.get400("/trade?year=" + "202", "Invalid Year Parameter");
        Assert.get400("/trade?year=", "Invalid Year Parameter");

        Assert.get400("/trade?sector=" + "X", "Invalid Sector Parameter");
        Assert.get400("/trade?sector=", "Invalid Sector Parameter");
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
        assertThat(dto.getTrades().size(), is(12));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), is("11.11.2023"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), is("05.04.2023"));
        assertThat(dto.getTrades().get(2).getPurchaseDate(), is("01.11.2022"));
        assertThat(dto.getTrades().get(3).getPurchaseDate(), is("10.05.2021"));
        assertThat(dto.getSums(), is(new String[]{"9", "4", "", "", "", "407.62", "2590769.35", "", "", "", "100.5", "10039.5", "2331.17", "30.24"}));
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
        assertThat(dto.getTrades().size(), is(7));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), is("05.04.2023"));
        assertThat(dto.getTrades().get(0).getTicker(), is("CEZ"));
        assertThat(dto.getTrades().get(0).getPurchaseTotal(), is("575599.35"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), is("01.11.2022"));
        assertThat(dto.getTrades().get(1).getTicker(), is("RR"));
        assertThat(dto.getTrades().get(1).getPurchaseTotal(), is("2000025"));
        assertThat(dto.getSums(), is(new String[]{"6", "3", "", "", "", "326.79", "2584081.02", "", "", "", "0", "0", "", ""}));
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
                .get("/trade?sector=" + Sector.SEMICONDUCTORS.getName())
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
        dto.setCompanyId("21322ef8-9e26-4eda-bf74-b0f0eb8925b1");
        dto.setDate("2020-01-01");
        dto.setPrice("100.5");
        dto.setQuantity("10");
        dto.setFees("15");

        TradeDto createdDto = given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/trade")
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradeDto.class);

        assertThat(createdDto.getTicker(), is("XTC"));
        assertThat(createdDto.getCurrency(), is(Currency.$));
        assertThat(createdDto.getPurchaseDate(), is("01.01.2020"));
        assertThat(createdDto.getPurchasePrice(), is(dto.getPrice()));
        assertThat(createdDto.getPurchaseQuantity(), is(dto.getQuantity()));
        assertThat(createdDto.getPurchaseFees(), is(dto.getFees()));
        assertThat(createdDto.getPurchaseTotal(), is("1020"));
        assertThat(createdDto.getSellDate(), is(nullValue()));
        assertThat(createdDto.getSellQuantity(), is(nullValue()));
        assertThat(createdDto.getSellPrice(), is(nullValue()));
        assertThat(createdDto.getSellFees(), is(nullValue()));
        assertThat(createdDto.getSellTotal(), is(nullValue()));
        assertThat(createdDto.getProfit(), is(nullValue()));
        assertThat(createdDto.getProfitPercentage(), is(nullValue()));

        TradesUiDto tradesDto = given().when()
                .get("/trade?companyId=" + dto.getCompanyId())
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(tradesDto.getTrades().size(), is(1));
        assertThat(tradesDto.getTrades().get(0).getTicker(), is("XTC"));
        assertThat(tradesDto.getSums(), is(new String[]{"1", "1", "", "", "", "15", "1020", "", "", "", "0", "0", "", ""}));
    }

    @Test
    void createTradeInvalidValues()
    {
        String validCompanyId = "21322ef8-9e26-4eda-bf74-b0f0eb8925b1";
        String validDate = "2020-01-01";
        String validPrice = "100.5";
        String validQuantity = "10";
        String validFees = "15";

        Assert.post400("/trade", null, "Payload is NULL");

        TradeCreateDto dto = new TradeCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setDate(validDate);
        dto.setPrice(validPrice);
        dto.setQuantity(validQuantity);

        dto.setFees(null);
        Assert.post400("/trade", dto, "Invalid Fees:");

        dto.setFees("");
        Assert.post400("/trade", dto, "Invalid Fees:");

        dto.setFees("x");
        Assert.post400("/trade", dto, "Invalid Fees:");

        dto.setFees("1.");
        Assert.post400("/trade", dto, "Invalid Fees:");

        dto.setFees(".1");
        Assert.post400("/trade", dto, "Invalid Fees:");

        dto.setFees("1234");
        Assert.post400("/trade", dto, "Invalid Fees:");

        dto.setFees("1.123");
        Assert.post400("/trade", dto, "Invalid Fees:");

        dto.setFees(validFees);
        dto.setPrice(null);
        Assert.post400("/trade", dto, "Invalid Price:");

        dto.setPrice("");
        Assert.post400("/trade", dto, "Invalid Price:");

        dto.setPrice("x");
        Assert.post400("/trade", dto, "Invalid Price:");

        dto.setPrice("1.");
        Assert.post400("/trade", dto, "Invalid Price:");

        dto.setPrice(".1");
        Assert.post400("/trade", dto, "Invalid Price:");

        dto.setPrice("1234567");
        Assert.post400("/trade", dto, "Invalid Price:");

        dto.setPrice("10.12345");
        Assert.post400("/trade", dto, "Invalid Price:");

        dto.setPrice(validPrice);
        dto.setQuantity(null);
        Assert.post400("/trade", dto, "Invalid Quantity:");

        dto.setQuantity("");
        Assert.post400("/trade", dto, "Invalid Quantity:");

        dto.setQuantity("x");
        Assert.post400("/trade", dto, "Invalid Quantity:");

        dto.setQuantity("1.");
        Assert.post400("/trade", dto, "Invalid Quantity:");

        dto.setQuantity(".1");
        Assert.post400("/trade", dto, "Invalid Quantity:");

        dto.setQuantity("12345");
        Assert.post400("/trade", dto, "Invalid Quantity:");

        dto.setQuantity("1.12345");
        Assert.post400("/trade", dto, "Invalid Quantity:");

        dto.setQuantity(validQuantity);
        dto.setDate(null);
        Assert.post400("/trade", dto, "Invalid Date:");

        dto.setDate("");
        Assert.post400("/trade", dto, "Invalid Date:");

        dto.setDate("01.01.2020");
        Assert.post400("/trade", dto, "Invalid Date:");

        dto.setDate("2020-1-1");
        Assert.post400("/trade", dto, "Invalid Date:");

        dto.setDate(validDate);
        dto.setCompanyId(null);
        Assert.post400("/trade", dto, "Invalid UUID");

        dto.setCompanyId("x");
        Assert.post400("/trade", dto, "Invalid UUID");

        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.post400("/trade", dto, "company with id '" + dto.getCompanyId() + "' not found");
    }

    @Test
    void sellTradeInvalidValues()
    {
        String validDate = "2020-01-01";
        String validPrice = "100.5";
        String validFees = "15";
        String validTradeId = "91d9253e-aee5-4d86-9c3e-18102bff698d";
        String validQuantity = "8";

        Assert.put400("/trade", null, "Payload is NULL");

        TradeSellDto dto = new TradeSellDto();
        dto.setDate(validDate);
        dto.setPrice(validPrice);
        dto.setFees(validFees);

        Assert.put400("/trade", dto, "No trades to sell provided");

        dto.setTrades(List.of(new TradeSellDto.Trade(validTradeId, validQuantity)));
        dto.setFees(null);
        Assert.put400("/trade", dto, "Invalid Fees:");

        dto.setFees("");
        Assert.put400("/trade", dto, "Invalid Fees:");

        dto.setFees("x");
        Assert.put400("/trade", dto, "Invalid Fees:");

        dto.setFees("1.");
        Assert.put400("/trade", dto, "Invalid Fees:");

        dto.setFees(".1");
        Assert.put400("/trade", dto, "Invalid Fees:");

        dto.setFees("1234");
        Assert.put400("/trade", dto, "Invalid Fees:");

        dto.setFees("1.123");
        Assert.put400("/trade", dto, "Invalid Fees:");

        dto.setFees(validFees);
        dto.setPrice(null);
        Assert.put400("/trade", dto, "Invalid Price:");

        dto.setPrice("");
        Assert.put400("/trade", dto, "Invalid Price:");

        dto.setPrice("x");
        Assert.put400("/trade", dto, "Invalid Price:");

        dto.setPrice("1.");
        Assert.put400("/trade", dto, "Invalid Price:");

        dto.setPrice(".1");
        Assert.put400("/trade", dto, "Invalid Price:");

        dto.setPrice("1234567");
        Assert.put400("/trade", dto, "Invalid Price:");

        dto.setPrice("10.12345");
        Assert.put400("/trade", dto, "Invalid Price:");

        dto.setPrice(validPrice);
        dto.getTrades().get(0).setQuantity(null);
        Assert.post400("/trade", dto, "Invalid Quantity:");

        dto.getTrades().get(0).setQuantity("");
        Assert.put400("/trade", dto, "Invalid Quantity:");

        dto.getTrades().get(0).setQuantity("x");
        Assert.put400("/trade", dto, "Invalid Quantity:");

        dto.getTrades().get(0).setQuantity("1.");
        Assert.put400("/trade", dto, "Invalid Quantity:");

        dto.getTrades().get(0).setQuantity(".1");
        Assert.put400("/trade", dto, "Invalid Quantity:");

        dto.getTrades().get(0).setQuantity("12345");
        Assert.put400("/trade", dto, "Invalid Quantity:");

        dto.getTrades().get(0).setQuantity("1.12345");
        Assert.put400("/trade", dto, "Invalid Quantity:");

        dto.getTrades().get(0).setQuantity("5.5");
        Assert.put400("/trade", dto, "unable to sell more than owned for tradeId='" + dto.getTrades().get(0).getTradeId() + "'");

        dto.getTrades().get(0).setQuantity(validQuantity);
        dto.setDate(null);
        Assert.put400("/trade", dto, "Invalid Date:");

        dto.setDate("");
        Assert.put400("/trade", dto, "Invalid Date:");

        dto.setDate("01.01.2020");
        Assert.put400("/trade", dto, "Invalid Date:");

        dto.setDate("2020-1-1");
        Assert.put400("/trade", dto, "Invalid Date:");

        dto.setDate(validDate);
        dto.getTrades().get(0).setTradeId(null);
        Assert.put400("/trade", dto, "Invalid UUID");

        dto.getTrades().get(0).setTradeId("x");
        Assert.put400("/trade", dto, "Invalid UUID");

        dto.getTrades().get(0).setTradeId(UUID.randomUUID().toString());
        Assert.put400("/trade", dto, "trade with id '" + dto.getTrades().get(0).getTradeId() + "' not found");
    }

    @Test
    void sellTrade()
    {
        TradeSellDto dto = new TradeSellDto();
        dto.setDate("2020-07-15");
        dto.setPrice("600");
        dto.setFees("15");
        dto.getTrades().add(new TradeSellDto.Trade("91d9253e-aee5-4d86-9c3e-18102bff698d", "5"));
        dto.getTrades().add(new TradeSellDto.Trade("19993bde-6d06-4006-918f-77baa8062e42", "2.5"));

        given().contentType(ContentType.JSON)
                .body(dto)
                .when()
                .put("/trade")
                .then()
                .statusCode(Response.Status.NO_CONTENT.getStatusCode());

        TradesUiDto tradesDto = given().when()
                .get("/trade?companyId=c65ea6ac-d848-46dd-98bc-9e3d99f39b21")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", TradesUiDto.class);

        assertThat(tradesDto.getTrades().size(), is(4));

        assertThat(tradesDto.getTrades().get(0).getTicker(), is("XTS"));
        assertThat(tradesDto.getTrades().get(0).getPurchaseDate(), is("01.05.2020"));
        assertThat(tradesDto.getTrades().get(0).getPurchaseQuantity(), is("2.5"));
        assertThat(tradesDto.getTrades().get(0).getPurchasePrice(), is("500"));
        assertThat(tradesDto.getTrades().get(0).getPurchaseFees(), is("3.33"));
        assertThat(tradesDto.getTrades().get(0).getSellDate(), is("15.07.2020"));
        assertThat(tradesDto.getTrades().get(0).getSellQuantity(), is("2.5"));
        assertThat(tradesDto.getTrades().get(0).getSellPrice(), is(dto.getPrice()));
        assertThat(tradesDto.getTrades().get(0).getSellFees(), is("5"));

        assertThat(tradesDto.getTrades().get(1).getTicker(), is("XTS"));
        assertThat(tradesDto.getTrades().get(1).getPurchaseDate(), is("01.05.2020"));
        assertThat(tradesDto.getTrades().get(1).getPurchaseQuantity(), is("5"));
        assertThat(tradesDto.getTrades().get(1).getPurchasePrice(), is("500"));
        assertThat(tradesDto.getTrades().get(1).getPurchaseFees(), is("6.67"));
        assertThat(tradesDto.getTrades().get(1).getSellDate(), is(nullValue()));
        assertThat(tradesDto.getTrades().get(1).getSellQuantity(), is(nullValue()));
        assertThat(tradesDto.getTrades().get(1).getSellPrice(), is(nullValue()));
        assertThat(tradesDto.getTrades().get(1).getSellFees(), is(nullValue()));

        assertThat(tradesDto.getTrades().get(2).getTicker(), is("XTS"));
        assertThat(tradesDto.getTrades().get(2).getPurchaseDate(), is("05.04.2020"));
        assertThat(tradesDto.getTrades().get(2).getPurchaseQuantity(), is("5"));
        assertThat(tradesDto.getTrades().get(2).getPurchasePrice(), is("450"));
        assertThat(tradesDto.getTrades().get(2).getPurchaseFees(), is("10"));
        assertThat(tradesDto.getTrades().get(2).getSellDate(), is("15.07.2020"));
        assertThat(tradesDto.getTrades().get(2).getSellQuantity(), is("5"));
        assertThat(tradesDto.getTrades().get(2).getSellPrice(), is(dto.getPrice()));
        assertThat(tradesDto.getTrades().get(2).getSellFees(), is("10"));

        assertThat(tradesDto.getTrades().get(3).getTicker(), is("XTS"));
        assertThat(tradesDto.getTrades().get(3).getPurchaseDate(), is("15.03.2020"));
        assertThat(tradesDto.getTrades().get(3).getPurchaseQuantity(), is("1"));
        assertThat(tradesDto.getTrades().get(3).getPurchasePrice(), is("400"));
        assertThat(tradesDto.getTrades().get(3).getPurchaseFees(), is("10"));
        assertThat(tradesDto.getTrades().get(3).getSellDate(), is(nullValue()));
        assertThat(tradesDto.getTrades().get(3).getSellQuantity(), is(nullValue()));
        assertThat(tradesDto.getTrades().get(3).getSellPrice(), is(nullValue()));
        assertThat(tradesDto.getTrades().get(3).getSellFees(), is(nullValue()));

        assertThat(tradesDto.getSums(), is(new String[]{"1", "1", "", "", "", "30", "6430", "", "", "", "15", "4485", "971.67", "27.66"}));
    }
}