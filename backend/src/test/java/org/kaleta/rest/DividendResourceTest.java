package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.DividendCreateDto;
import org.kaleta.dto.DividendDto;
import org.kaleta.dto.DividendsUiDto;
import org.kaleta.entity.Currency;
import org.kaleta.framework.Assert;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class DividendResourceTest
{
    @Test
    void parameterValidator()
    {
        Assert.get400("/dividend?companyId=" + "AAAAAA", "Invalid UUID Parameter");
        Assert.get400("/dividend?companyId=", "Invalid UUID Parameter");

        Assert.get400("/dividend?currency=" + "X", "Invalid Currency Parameter");
        Assert.get400("/dividend?currency=", "Invalid Currency Parameter");

        Assert.get400("/dividend?year=" + "20x2", "Invalid Year Parameter");
        Assert.get400("/dividend?year=" + "20222", "Invalid Year Parameter");
        Assert.get400("/dividend?year=" + "202", "Invalid Year Parameter");
        Assert.get400("/dividend?year=", "Invalid Year Parameter");
    }

    @Test
    void getDividendsFilterNone()
    {
        DividendsUiDto dto = given().when()
                .get("/dividend")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", DividendsUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getDividends().size(), is(4));
        assertThat(dto.getDividends().get(0).getDate(), is("01.12.2022"));
        assertThat(dto.getDividends().get(1).getDate(), is("01.12.2021"));
        assertThat(dto.getDividends().get(2).getDate(), is("01.06.2021"));
        assertThat(dto.getDividends().get(3).getDate(), is("01.12.2020"));
        assertThat(dto.getSums(), is(new String[]{"3", "2", "", "2150", "215", "1935"}));
    }

    @Test
    void getDividendsFilterCurrency()
    {
        DividendsUiDto dto = given().when()
                .get("/dividend?currency=K")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", DividendsUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getDividends().size(), is(1));
        assertThat(dto.getDividends().get(0).getTicker(), is("CEZ"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "1000", "100", "900"}));
    }

    @Test
    void getDividendsFilterCompany()
    {
        DividendsUiDto dto = given().when()
                .get("/dividend?companyId=66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", DividendsUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getDividends().size(), is(1));
        assertThat(dto.getDividends().get(0).getTicker(), is("ABCD"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "1000", "100", "900"}));
    }

    @Test
    void getDividendsFilterNonExistentCompany()
    {
        DividendsUiDto dto = given().when()
                .get("/dividend?companyId=2df6b65f-54fb-4381-9b38-8c25409fe168")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", DividendsUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getDividends().size(), is(0));
    }

    @Test
    void getDividendsFilterYear()
    {
        DividendsUiDto dto = given().when()
                .get("/dividend?year=2020")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", DividendsUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getDividends().size(), is(1));
        assertThat(dto.getDividends().get(0).getTicker(), is("ABCD"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "1000", "100", "900"}));
    }

    @Test
    void getDividendsFilterMultiple()
    {
        DividendsUiDto dto = given().when()
                .get("/dividend?year=2021&companyId=adb89a0a-86bc-4854-8a55-058ad2e6308f")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", DividendsUiDto.class);

        assertThat(dto.getColumns().size(), is(6));
        assertThat(dto.getColumns().get(1), is("#"));
        assertThat(dto.getDividends().size(), is(1));
        assertThat(dto.getDividends().get(0).getTicker(), is("NVDA"));
        assertThat(dto.getSums(), is(new String[]{"1", "1", "", "70", "7", "63"}));
    }

    @Test
    void createDividend()
    {
        DividendCreateDto dto = new DividendCreateDto();
        dto.setCompanyId("21322ef8-9e26-4eda-bf74-b0f0eb8925b1");
        dto.setDate("2020-01-01");
        dto.setDividend("100.5");
        dto.setTax("10.5");

        DividendDto createdDto = given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/dividend")
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", DividendDto.class);

        assertThat(createdDto.getTicker(), is("XTC"));
        assertThat(createdDto.getCurrency(), is(Currency.$));
        assertThat(createdDto.getDate(), is("01.01.2020"));
        assertThat(createdDto.getDividend(), is(dto.getDividend()));
        assertThat(createdDto.getTax(), is(dto.getTax()));
        assertThat(createdDto.getTotal(), is("90"));

        DividendsUiDto dividendsDto = given().when()
                .get("/dividend?companyId=" + dto.getCompanyId())
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", DividendsUiDto.class);

        assertThat(dividendsDto.getDividends().size(), is(1));
        assertThat(dividendsDto.getDividends().get(0).getTicker(), is("XTC"));
        assertThat(dividendsDto.getSums(), is(new String[]{"1", "1", "", "100.5", "10.5", "90"}));
    }

    @Test
    void createDividendInvalidValues()
    {
        String validCompanyId = "21322ef8-9e26-4eda-bf74-b0f0eb8925b1";
        String validDate = "2020-01-01";
        String validDividend = "100.5";
        String validTax = "100.5";

        Assert.post400("/dividend", null, "Payload is NULL");

        DividendCreateDto dto = new DividendCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setDate(validDate);
        dto.setDividend(validDividend);

        dto.setTax(null);
        Assert.post400("/dividend", dto, "Invalid Tax:");

        dto.setTax("");
        Assert.post400("/dividend", dto, "Invalid Tax:");

        dto.setTax("x");
        Assert.post400("/dividend", dto, "Invalid Tax:");

        dto.setTax("1.");
        Assert.post400("/dividend", dto, "Invalid Tax:");

        dto.setTax(".1");
        Assert.post400("/dividend", dto, "Invalid Tax:");

        dto.setTax("12345");
        Assert.post400("/dividend", dto, "Invalid Tax:");

        dto.setTax("1.123");
        Assert.post400("/dividend", dto, "Invalid Tax:");

        dto.setTax(validTax);
        dto.setDividend(null);
        Assert.post400("/dividend", dto, "Invalid Dividend:");

        dto.setDividend("");
        Assert.post400("/dividend", dto, "Invalid Dividend:");

        dto.setDividend("x");
        Assert.post400("/dividend", dto, "Invalid Dividend:");

        dto.setDividend("1.");
        Assert.post400("/dividend", dto, "Invalid Dividend:");

        dto.setDividend(".1");
        Assert.post400("/dividend", dto, "Invalid Dividend:");

        dto.setDividend("123456");
        Assert.post400("/dividend", dto, "Invalid Dividend:");

        dto.setDividend("1.123");
        Assert.post400("/dividend", dto, "Invalid Dividend:");

        dto.setDividend(validDividend);
        dto.setDate(null);
        Assert.post400("/dividend", dto, "Invalid Date:");

        dto.setDate("");
        Assert.post400("/dividend", dto, "Invalid Date:");

        dto.setDate("01.01.2020");
        Assert.post400("/dividend", dto, "Invalid Date:");

        dto.setDate("2020-1-1");
        Assert.post400("/dividend", dto, "Invalid Date:");

        dto.setDate(validDate);
        dto.setCompanyId(null);
        Assert.post400("/dividend", dto, "Invalid UUID");

        dto.setCompanyId("x");
        Assert.post400("/dividend", dto, "Invalid UUID");

        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.post400("/dividend", dto, "company with id '" + dto.getCompanyId() + "' not found");
    }
}