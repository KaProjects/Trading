package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.FinancialCreateDto;
import org.kaleta.dto.RecordsUiDto;
import org.kaleta.framework.Assert;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class FinancialResourceTest
{

    @Test
    void createFinancial()
    {
        FinancialCreateDto dto = new FinancialCreateDto();
        dto.setCompanyId("6877c444-00ee-4af5-99ef-415980484d8c");
        dto.setQuarter("23Q2");
        dto.setRevenue("2000");
        dto.setCostGoodsSold("1000");
        dto.setOperatingExpenses("500");
        dto.setNetIncome("200");


        given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/financial")
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode());

        RecordsUiDto recordsUiDto = given().when()
                .get("/record/" + dto.getCompanyId())
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(recordsUiDto.getCompany().getTicker(), is("XFC"));
        assertThat(recordsUiDto.getRecords().size(), is(1));
        assertThat(recordsUiDto.getFinancials().getValues().size(), is(2));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getQuarter(), is(dto.getQuarter()));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getRevenue(), is("2B"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getCostGoodsSold(), is("1000M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getGrossProfit(), is("1000M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getGrossMargin(), is("50"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getOperatingExpenses(), is("500M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getOperatingIncome(), is("500M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getOperatingMargin(), is("25"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getNetIncome(), is("200M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getNetMargin(), is("10"));

        assertThat(recordsUiDto.getLatest().getPrice().getValue(), is("60"));
        assertThat(recordsUiDto.getFinancials().getTtm().getRevenue(), is("6B"));
        assertThat(recordsUiDto.getFinancials().getTtm().getCostGoodsSold(), is("6B"));
        assertThat(recordsUiDto.getFinancials().getTtm().getGrossProfit(), is("3.8B"));
        assertThat(recordsUiDto.getFinancials().getTtm().getGrossMargin(), is("63"));
        assertThat(recordsUiDto.getFinancials().getTtm().getOperatingExpenses(), is("1.1B"));
        assertThat(recordsUiDto.getFinancials().getTtm().getOperatingIncome(), is("2.7B"));
        assertThat(recordsUiDto.getFinancials().getTtm().getOperatingMargin(), is("45"));
        assertThat(recordsUiDto.getFinancials().getTtm().getNetIncome(), is("2B"));
        assertThat(recordsUiDto.getFinancials().getTtm().getNetMargin(), is("33"));
    }

    @Test
    void createFinancialQuarters()
    {
        String companyId = "6877c555-00ee-4af5-99ef-415980484d8c";

        FinancialCreateDto dtoQ2 = new FinancialCreateDto();
        dtoQ2.setCompanyId(companyId);
        dtoQ2.setQuarter("23Q2");
        dtoQ2.setRevenue("2000");
        dtoQ2.setCostGoodsSold("1000");
        dtoQ2.setOperatingExpenses("500");
        dtoQ2.setNetIncome("200");

        given().contentType(ContentType.JSON)
                .body(dtoQ2)
                .when().post("/financial")
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode());

        FinancialCreateDto dtoH2 = new FinancialCreateDto();
        dtoH2.setCompanyId(companyId);
        dtoH2.setQuarter("24H2");
        dtoH2.setRevenue("3000");
        dtoH2.setCostGoodsSold("1000");
        dtoH2.setOperatingExpenses("500");
        dtoH2.setNetIncome("300");

        given().contentType(ContentType.JSON)
                .body(dtoH2)
                .when().post("/financial")
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode());

        FinancialCreateDto dtoY = new FinancialCreateDto();
        dtoY.setCompanyId(companyId);
        dtoY.setQuarter("25FY");
        dtoY.setRevenue("5000");
        dtoY.setCostGoodsSold("2000");
        dtoY.setOperatingExpenses("1000");
        dtoY.setNetIncome("500");

        given().contentType(ContentType.JSON)
                .body(dtoY)
                .when().post("/financial")
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode());

        RecordsUiDto recordsUiDto = given().when()
                .get("/record/" + companyId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(recordsUiDto.getCompany().getTicker(), is("XFQ"));
        assertThat(recordsUiDto.getRecords().size(), is(1));
        assertThat(recordsUiDto.getFinancials().getValues().size(), is(4));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getQuarter(), is(dtoY.getQuarter()));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getRevenue(), is("5B"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getCostGoodsSold(), is("2B"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getGrossProfit(), is("3B"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getGrossMargin(), is("60"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getOperatingExpenses(), is("1000M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getOperatingIncome(), is("2B"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getOperatingMargin(), is("40"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getNetIncome(), is("500M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getNetMargin(), is("10"));

        assertThat(recordsUiDto.getFinancials().getValues().get(1).getQuarter(), is(dtoH2.getQuarter()));
        assertThat(recordsUiDto.getFinancials().getValues().get(1).getRevenue(), is("3B"));
        assertThat(recordsUiDto.getFinancials().getValues().get(1).getCostGoodsSold(), is("1000M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(1).getGrossProfit(), is("2B"));
        assertThat(recordsUiDto.getFinancials().getValues().get(1).getGrossMargin(), is("67"));
        assertThat(recordsUiDto.getFinancials().getValues().get(1).getOperatingExpenses(), is("500M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(1).getOperatingIncome(), is("1.5B"));
        assertThat(recordsUiDto.getFinancials().getValues().get(1).getOperatingMargin(), is("50"));
        assertThat(recordsUiDto.getFinancials().getValues().get(1).getNetIncome(), is("300M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(1).getNetMargin(), is("10"));

        assertThat(recordsUiDto.getFinancials().getValues().get(2).getQuarter(), is(dtoQ2.getQuarter()));
        assertThat(recordsUiDto.getFinancials().getValues().get(2).getRevenue(), is("2B"));
        assertThat(recordsUiDto.getFinancials().getValues().get(2).getCostGoodsSold(), is("1000M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(2).getGrossProfit(), is("1000M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(2).getGrossMargin(), is("50"));
        assertThat(recordsUiDto.getFinancials().getValues().get(2).getOperatingExpenses(), is("500M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(2).getOperatingIncome(), is("500M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(2).getOperatingMargin(), is("25"));
        assertThat(recordsUiDto.getFinancials().getValues().get(2).getNetIncome(), is("200M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(2).getNetMargin(), is("10"));

        assertThat(recordsUiDto.getFinancials().getValues().get(3).getQuarter(), is("23Q1"));
    }

    @Test
    void createFinancial_invalidParameters()
    {
        String validCompanyId = "6877c444-00ee-4af5-99ef-415980484d8c";
        String validQuarter = "23Q2";
        String validRevenue = "2000";
        String validCogs = "1000";
        String validOpExp = "500";
        String validNetIncome = "200";

        Assert.post400("/financial", null, "Payload is NULL");

        FinancialCreateDto dto = new FinancialCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setQuarter(validQuarter);
        dto.setRevenue(validRevenue);
        dto.setCostGoodsSold(validCogs);
        dto.setOperatingExpenses(validOpExp);
        dto.setNetIncome(validNetIncome);

        dto.setCostGoodsSold(null);
        Assert.post400("/financial", dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold("");
        Assert.post400("/financial", dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold("x");
        Assert.post400("/financial", dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold("1.");
        Assert.post400("/financial", dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold(".1");
        Assert.post400("/financial", dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold("1234567");
        Assert.post400("/financial", dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold("1.123");
        Assert.post400("/financial", dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold(validCogs);
        dto.setOperatingExpenses(null);
        Assert.post400("/financial", dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses("");
        Assert.post400("/financial", dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses("x");
        Assert.post400("/financial", dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses("1.");
        Assert.post400("/financial", dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses(".1");
        Assert.post400("/financial", dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses("1234567");
        Assert.post400("/financial", dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses("1.123");
        Assert.post400("/financial", dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses(validOpExp);
        dto.setNetIncome(null);
        Assert.post400("/financial", dto, "Invalid Net Income:");

        dto.setNetIncome("");
        Assert.post400("/financial", dto, "Invalid Net Income:");

        dto.setNetIncome("x");
        Assert.post400("/financial", dto, "Invalid Net Income:");

        dto.setNetIncome("1.");
        Assert.post400("/financial", dto, "Invalid Net Income:");

        dto.setNetIncome(".1");
        Assert.post400("/financial", dto, "Invalid Net Income:");

        dto.setNetIncome("1234567");
        Assert.post400("/financial", dto, "Invalid Net Income:");

        dto.setNetIncome("1.123");
        Assert.post400("/financial", dto, "Invalid Net Income:");

        dto.setNetIncome(validNetIncome);
        dto.setRevenue(null);
        Assert.post400("/financial", dto, "Invalid Revenue:");

        dto.setRevenue("");
        Assert.post400("/financial", dto, "Invalid Revenue:");

        dto.setRevenue("x");
        Assert.post400("/financial", dto, "Invalid Revenue:");

        dto.setRevenue("1.");
        Assert.post400("/financial", dto, "Invalid Revenue:");

        dto.setRevenue(".1");
        Assert.post400("/financial", dto, "Invalid Revenue:");

        dto.setRevenue("1234567");
        Assert.post400("/financial", dto, "Invalid Revenue:");

        dto.setRevenue("1.123");
        Assert.post400("/financial", dto, "Invalid Revenue:");

        dto.setRevenue(validRevenue);
        dto.setCompanyId(null);
        Assert.post400("/financial", dto, "Invalid UUID");

        dto.setCompanyId("x");
        Assert.post400("/financial", dto, "Invalid UUID");

        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.post400("/financial", dto, "company with id '" + dto.getCompanyId() + "' not found");
        dto.setCompanyId(validCompanyId);

        dto.setQuarter(null);
        Assert.post400("/financial", dto, "Invalid Quarter:");

        dto.setQuarter("");
        Assert.post400("/financial", dto, "Invalid Quarter:");

        dto.setQuarter("x");
        Assert.post400("/financial", dto, "Invalid Quarter:");

        dto.setQuarter("2023Q3");
        Assert.post400("/financial", dto, "Invalid Quarter:");

        dto.setQuarter("23Q5");
        Assert.post400("/financial", dto, "Invalid Quarter:");

        dto.setQuarter("23H3");
        Assert.post400("/financial", dto, "Invalid Quarter:");

        dto.setQuarter("23Y2");
        Assert.post400("/financial", dto, "Invalid Quarter:");
    }
}