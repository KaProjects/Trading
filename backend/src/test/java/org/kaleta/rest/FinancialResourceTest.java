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
        dto.setNetIncome("200");
        dto.setEps("2");

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
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getNetIncome(), is("200M"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getNetMargin(), is("10"));
        assertThat(recordsUiDto.getFinancials().getValues().get(0).getEps(), is("2"));
        assertThat(recordsUiDto.getLatest().getPrice().getValue(), is("60"));
        assertThat(recordsUiDto.getFinancials().getTtm().getRevenue(), is("6B"));
        assertThat(recordsUiDto.getFinancials().getTtm().getNetIncome(), is("600M"));
        assertThat(recordsUiDto.getFinancials().getTtm().getNetMargin(), is("10"));
        assertThat(recordsUiDto.getFinancials().getTtm().getEps(), is("6"));
        assertThat(recordsUiDto.getFinancials().getTtm().getTtmPe(), is("10"));
        assertThat(recordsUiDto.getFinancials().getTtm().getForwardPe(), is("7.5"));
    }

    @Test
    void createFinancialInvalidValues()
    {
        String validCompanyId = "6877c444-00ee-4af5-99ef-415980484d8c";
        String validQuarter = "23Q2";
        String validRevenue = "2000";
        String validNetIncome = "200";
        String validEps = "2";

        Assert.post400("/financial", null, "Payload is NULL");

        FinancialCreateDto dto = new FinancialCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setQuarter(validQuarter);
        dto.setRevenue(validRevenue);
        dto.setNetIncome(validNetIncome);

        Assert.post400("/financial", dto, "Invalid EPS:");

        dto.setEps("");
        Assert.post400("/financial", dto, "Invalid EPS:");

        dto.setEps("x");
        Assert.post400("/financial", dto, "Invalid EPS:");

        dto.setEps("1.");
        Assert.post400("/financial", dto, "Invalid EPS:");

        dto.setEps(".1");
        Assert.post400("/financial", dto, "Invalid EPS:");

        dto.setEps("123");
        Assert.post400("/financial", dto, "Invalid EPS:");

        dto.setEps("1.123");
        Assert.post400("/financial", dto, "Invalid EPS:");

        dto.setEps(validEps);
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
    }
}