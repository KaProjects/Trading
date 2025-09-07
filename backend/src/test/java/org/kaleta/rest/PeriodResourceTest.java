package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.PeriodCreateDto;
import org.kaleta.dto.PeriodDto;
import org.kaleta.dto.ResearchUiDto;
import org.kaleta.framework.Assert;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class PeriodResourceTest
{
    private final String path = "/period";

    @Test
    void update()
    {
        String newName = "20H2";
        String newEndingMonth = "2011";
        String newReportDate = "2020-12-15";
        String newShares = "12345";
        String newPriceLatest = "23.5";
        String newPriceLow = "20.5";
        String newPriceHigh = "26.5";
        String newResearch = "[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\"saasdasdaa\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"as\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"das\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"s\"}]}]}]";
        String newRevenue = "22.5";
        String newCostGoodsSold = "5";
        String newOperatingExpenses = "10";
        String newNetIncome = "5";
        String newDividends = "2";

        PeriodDto dto = new PeriodDto();
        dto.setId("550e8400-e29b-41d4-a716-446655440000");
        dto.setName(newName);
        dto.setEndingMonth(newEndingMonth);
        dto.setReportDate(newReportDate);
        dto.setShares(newShares);
        dto.setPriceLow(newPriceLow);
        dto.setPriceHigh(newPriceHigh);
        dto.setResearch(newResearch);
        dto.setRevenue(newRevenue);
        dto.setCostGoodsSold(newCostGoodsSold);
        dto.setOperatingExpenses(newOperatingExpenses);
        dto.setNetIncome(newNetIncome);
        dto.setDividend(newDividends);

        Assert.put204(path, dto);

        ResearchUiDto researchDto = given().when()
                .get("/research/e7c49260-53da-42c1-80cf-eccf6ed928a7")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", ResearchUiDto.class);

        assertThat(researchDto.getCompany().getTicker(), is("XXX"));
        assertThat(researchDto.getPeriods().size(), is(1));
        assertThat(researchDto.getPeriods().get(0).getName(), is(newName));
        assertThat(researchDto.getPeriods().get(0).getEndingMonth(), is("11/2020"));
        assertThat(researchDto.getPeriods().get(0).getReportDate(), is("15.12.2020"));
        assertThat(researchDto.getPeriods().get(0).getShares(), is("12.35B"));
        assertThat(researchDto.getPeriods().get(0).getPriceHigh(), is(newPriceHigh));
        assertThat(researchDto.getPeriods().get(0).getPriceLow(), is(newPriceLow));
        assertThat(researchDto.getPeriods().get(0).getResearch(), is(newResearch));
        assertThat(researchDto.getPeriods().get(0).getRevenue(), is(newRevenue + "M"));
        assertThat(researchDto.getPeriods().get(0).getCostGoodsSold(), is(newCostGoodsSold + "M"));
        assertThat(researchDto.getPeriods().get(0).getOperatingExpenses(), is(newOperatingExpenses + "M"));
        assertThat(researchDto.getPeriods().get(0).getNetIncome(), is(newNetIncome + "M"));
        assertThat(researchDto.getPeriods().get(0).getDividend(), is(newDividends + "M"));
    }

    @Test
    void updateOnlyResearch()
    {
        ResearchUiDto researchDtoBefore = given().when()
                .get("/research/0a16ba1d-99de-4306-8fc5-81ee11b60ea0")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", ResearchUiDto.class);

        assertThat(researchDtoBefore.getPeriods().size(), is(1));

        PeriodDto beforeDto = researchDtoBefore.getPeriods().get(0);

        String newResearch = "XXXXXXXXX";
        PeriodDto dto = new PeriodDto();
        dto.setId(beforeDto.getId());
        dto.setResearch(newResearch);

        Assert.put204(path, dto);

        ResearchUiDto researchDtoAfter = given().when()
                .get("/research/0a16ba1d-99de-4306-8fc5-81ee11b60ea0")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", ResearchUiDto.class);

        assertThat(researchDtoAfter.getPeriods().size(), is(1));

        PeriodDto afterDto = researchDtoAfter.getPeriods().get(0);

        assertThat(afterDto.getId(), is(beforeDto.getId()));
        assertThat(afterDto.getName(), is(beforeDto.getName()));
        assertThat(afterDto.getEndingMonth(), is(beforeDto.getEndingMonth()));
        assertThat(afterDto.getReportDate(), is(beforeDto.getReportDate()));
        assertThat(afterDto.getPriceHigh(), is(beforeDto.getPriceHigh()));
        assertThat(afterDto.getPriceLow(), is(beforeDto.getPriceLow()));
        assertThat(afterDto.getResearch(), is(newResearch));
        assertThat(afterDto.getRevenue(), is(beforeDto.getRevenue()));
        assertThat(afterDto.getCostGoodsSold(), is(beforeDto.getCostGoodsSold()));
        assertThat(afterDto.getOperatingExpenses(), is(beforeDto.getOperatingExpenses()));
        assertThat(afterDto.getNetIncome(), is(beforeDto.getNetIncome()));
        assertThat(afterDto.getDividend(), is(beforeDto.getDividend()));

        assertThat(researchDtoAfter.getTtm(), is(nullValue()));
        assertThat(researchDtoAfter.getFinancials().size(), is(0));
    }

    @Test
    void updateInvalidValues()
    {
        Assert.put400(path, null, "Payload is NULL");

        PeriodDto dto =  new PeriodDto();
        Assert.put400(path, dto, "Invalid UUID Parameter:");

        dto.setId("x");
        Assert.put400(path, dto, "Invalid UUID Parameter:");

        dto.setId(UUID.randomUUID().toString());
        Assert.put400(path, dto, "period with id '" + dto.getId() + "' not found");

        dto.setId("2ccbf4fe-dbe7-4c40-a2a2-49bf79f15dad");
        dto.setName("");
        Assert.put400(path, dto, "Invalid Name:");

        dto.setName("FY2025");
        Assert.put400(path, dto, "Invalid Name:");

        dto.setName(null);
        dto.setReportDate("12.2020.05");
        Assert.put400(path, dto, "Invalid Report Date:");

        dto.setReportDate("1.1.2020");
        Assert.put400(path, dto, "Invalid Report Date:");

        dto.setReportDate(null);
        dto.setEndingMonth("");
        Assert.put400(path, dto, "Invalid Ending Month:");

        dto.setEndingMonth("xyz6");
        Assert.put400(path, dto, "Invalid Ending Month:");

        dto.setEndingMonth("202510");
        Assert.put400(path, dto, "Invalid Ending Month:");

        dto.setEndingMonth(null);
        dto.setShares("x");
        Assert.put400(path, dto, "Invalid Shares:");

        dto.setShares(".1");
        Assert.put400(path, dto, "Invalid Shares:");

        dto.setShares("1.");
        Assert.put400(path, dto, "Invalid Shares:");

        dto.setShares("1234567");
        Assert.put400(path, dto, "Invalid Shares:");

        dto.setShares("10.123");
        Assert.put400(path, dto, "Invalid Shares:");

        dto.setShares(null);
        dto.setPriceHigh("x");
        Assert.put400(path, dto, "Invalid High Price:");

        dto.setPriceHigh(".1");
        Assert.put400(path, dto, "Invalid High Price:");

        dto.setPriceHigh("1.");
        Assert.put400(path, dto, "Invalid High Price:");

        dto.setPriceHigh("1234567");
        Assert.put400(path, dto, "Invalid High Price:");

        dto.setPriceHigh("10.12345");
        Assert.put400(path, dto, "Invalid High Price:");

        dto.setPriceHigh(null);
        dto.setPriceLow("x");
        Assert.put400(path, dto, "Invalid Low Price:");

        dto.setPriceLow(".1");
        Assert.put400(path, dto, "Invalid Low Price:");

        dto.setPriceLow("1.");
        Assert.put400(path, dto, "Invalid Low Price:");

        dto.setPriceLow("1234567");
        Assert.put400(path, dto, "Invalid Low Price:");

        dto.setPriceLow("10.12345");
        Assert.put400(path, dto, "Invalid Low Price:");

        dto.setPriceLow(null);
        dto.setRevenue("x");
        Assert.put400(path, dto, "Invalid Revenue:");

        dto.setRevenue(".1");
        Assert.put400(path, dto, "Invalid Revenue:");

        dto.setRevenue("1.");
        Assert.put400(path, dto, "Invalid Revenue:");

        dto.setRevenue("1234567");
        Assert.put400(path, dto, "Invalid Revenue:");

        dto.setRevenue("10.123");
        Assert.put400(path, dto, "Invalid Revenue:");

        dto.setRevenue(null);
        dto.setCostGoodsSold("x");
        Assert.put400(path, dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold(".1");
        Assert.put400(path, dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold("1.");
        Assert.put400(path, dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold("1234567");
        Assert.put400(path, dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold("10.123");
        Assert.put400(path, dto, "Invalid Cost of Goods Sold:");

        dto.setCostGoodsSold(null);
        dto.setOperatingExpenses("x");
        Assert.put400(path, dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses(".1");
        Assert.put400(path, dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses("1.");
        Assert.put400(path, dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses("1234567");
        Assert.put400(path, dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses("10.123");
        Assert.put400(path, dto, "Invalid Operating Expenses:");

        dto.setOperatingExpenses(null);
        dto.setNetIncome("x");
        Assert.put400(path, dto, "Invalid Net Income:");

        dto.setNetIncome(".1");
        Assert.put400(path, dto, "Invalid Net Income:");

        dto.setNetIncome("1.");
        Assert.put400(path, dto, "Invalid Net Income:");

        dto.setNetIncome("1234567");
        Assert.put400(path, dto, "Invalid Net Income:");

        dto.setNetIncome("10.123");
        Assert.put400(path, dto, "Invalid Net Income:");

        dto.setNetIncome(null);
        dto.setDividend("x");
        Assert.put400(path, dto, "Invalid Dividends:");

        dto.setDividend(".1");
        Assert.put400(path, dto, "Invalid Dividends:");

        dto.setDividend("1.");
        Assert.put400(path, dto, "Invalid Dividends:");

        dto.setDividend("1234567");
        Assert.put400(path, dto, "Invalid Dividends:");

        dto.setDividend("10.123");
        Assert.put400(path, dto, "Invalid Dividends:");
    }

    @Test
    void create()
    {
        String companyId = "6a1e9d75-63b3-45a0-9ed7-dc38cfd22551";
        PeriodCreateDto dto = new PeriodCreateDto();
        dto.setCompanyId(companyId);
        dto.setName("FY15");
        dto.setEndingMonth("1510");
        dto.setReportDate("2015-11-11");

        Assert.post201(path, dto);

        ResearchUiDto researchDto = given().when()
                .get("/research/" + companyId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", ResearchUiDto.class);

        assertThat(researchDto.getCompany().getTicker(), is("ZZZ"));
        assertThat(researchDto.getPeriods().size(), is(1));
        assertThat(researchDto.getPeriods().get(0).getName(), is(dto.getName()));
        assertThat(researchDto.getPeriods().get(0).getEndingMonth(), is("10/2015"));
        assertThat(researchDto.getPeriods().get(0).getReportDate(), is("11.11.2015"));
        assertThat(researchDto.getPeriods().get(0).getPriceHigh(), is(blankString()));
        assertThat(researchDto.getPeriods().get(0).getPriceLow(), is(blankString()));
        assertThat(researchDto.getPeriods().get(0).getRevenue(), is(blankString()));
        assertThat(researchDto.getPeriods().get(0).getCostGoodsSold(), is(blankString()));
        assertThat(researchDto.getPeriods().get(0).getOperatingExpenses(), is(blankString()));
        assertThat(researchDto.getPeriods().get(0).getNetIncome(), is(blankString()));
    }

    @Test
    void createInvalidValues()
    {
        String validCompanyId = "d98c9ea1-ef2a-400a-bc7f-00d90e5d8e10";
        String validName = "FY19";
        String validEndingMonth = "1911";
        String validReportDate = "2020-01-01";

        Assert.post400(path, null, "Payload is NULL");

        PeriodCreateDto dto = new PeriodCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setEndingMonth(validEndingMonth);
        dto.setReportDate(validReportDate);

        Assert.post400(path, dto, "Invalid Name:");

        dto.setName("");
        Assert.post400(path, dto, "Invalid Name:");

        dto.setName("FY2025");
        Assert.post400(path, dto, "Invalid Name:");

        dto.setName(validName);
        dto.setEndingMonth(null);
        Assert.post400(path, dto, "Invalid Ending Month:");

        dto.setEndingMonth("");
        Assert.post400(path, dto, "Invalid Ending Month:");

        dto.setEndingMonth("xyz6");
        Assert.post400(path, dto, "Invalid Ending Month:");

        dto.setEndingMonth("202510");
        Assert.post400(path, dto, "Invalid Ending Month:");

        dto.setEndingMonth(validEndingMonth);
        dto.setReportDate("12.2020.05");
        Assert.post400(path, dto, "Invalid Report Date:");

        dto.setReportDate("1.1.2020");
        Assert.post400(path, dto, "Invalid Report Date:");

        dto.setReportDate(null);
        dto.setCompanyId(null);
        Assert.post400(path, dto, "Invalid UUID");

        dto.setCompanyId("x");
        Assert.post400(path, dto, "Invalid UUID");

        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.post400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");
    }

}