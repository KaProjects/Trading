package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeDto;
import org.kaleta.dto.TradesUiDto;
import org.kaleta.entity.Currency;
import org.kaleta.framework.Assert;

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
        assertThat(dto.getTrades().size(), is(8));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), is("11.11.2023"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), is("05.04.2023"));
        assertThat(dto.getTrades().get(2).getPurchaseDate(), is("01.11.2022"));
        assertThat(dto.getTrades().get(3).getPurchaseDate(), is("10.05.2021"));
        assertThat(dto.getSums(), is(new String[]{"8", "4", "", "", "", "377.62", "2584339.35", "", "", "", "85.5", "5725.5", "1530.5", "36.48"}));
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
        assertThat(dto.getTrades().size(), is(5));
        assertThat(dto.getTrades().get(0).getPurchaseDate(), is("05.04.2023"));
        assertThat(dto.getTrades().get(0).getTicker(), is("CEZ"));
        assertThat(dto.getTrades().get(0).getPurchaseTotal(), is("575599.35"));
        assertThat(dto.getTrades().get(1).getPurchaseDate(), is("01.11.2022"));
        assertThat(dto.getTrades().get(1).getTicker(), is("RR"));
        assertThat(dto.getTrades().get(1).getPurchaseTotal(), is("2000025"));
        assertThat(dto.getSums(), is(new String[]{"5", "3", "", "", "", "310.12", "2581164.35", "", "", "", "0", "0", "", ""}));
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
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "", "", "18", "2028", "", "", "", "30.5", "3070.5", "1042.5", "51.41"}));
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
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "", "", "14.5", "2017", "", "", "", "50", "2550", "533", "26.43"}));
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
        assertThat(dto.getSums(), is(new String[]{"3", "3", "", "", "", "282.62", "579644.35", "", "", "", "80.5", "5620.5", "1575.5", "38.95"}));

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
        assertThat(dtoZeroPurchase.getSums(), is(new String[]{"1", "1", "", "", "", "0", "0", "", "", "", "5", "105", "", ""}));

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
        dto.setDate("01.01.2020");
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

        assertThat(createdDto.getTicker(), is("XCC"));
        assertThat(createdDto.getCurrency(), is(Currency.$));
        assertThat(createdDto.getPurchaseDate(), is(dto.getDate()));
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
        assertThat(tradesDto.getTrades().get(0).getTicker(), is("XCC"));
        assertThat(tradesDto.getSums(), is(new String[]{"1", "1", "", "", "", "15", "1020", "", "", "", "0", "0", "", ""}));
    }

    @Test
    void createTradeInvalidValues()
    {
        String validCompanyId = "21322ef8-9e26-4eda-bf74-b0f0eb8925b1";
        String validDate = "01.01.2020";
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

        dto.setFees("123456");
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

        dto.setPrice("12345678901");
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

        dto.setQuantity("123456789");
        Assert.post400("/trade", dto, "Invalid Quantity:");

        dto.setQuantity("1.12345");
        Assert.post400("/trade", dto, "Invalid Quantity:");

        dto.setQuantity(validQuantity);
        dto.setDate(null);
        Assert.post400("/trade", dto, "Invalid Date:");

        dto.setDate("");
        Assert.post400("/trade", dto, "Invalid Date:");

        dto.setDate("1.1.2020");
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
    }
}