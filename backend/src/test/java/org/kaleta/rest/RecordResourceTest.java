package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.RecordCreateDto;
import org.kaleta.dto.RecordDto;
import org.kaleta.dto.RecordsUiDto;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Sector;
import org.kaleta.framework.Assert;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class RecordResourceTest
{
    @Test
    void getRecords()
    {
        RecordsUiDto dto = given().when()
                .get("/record/adb89a0a-86bc-4854-8a55-058ad2e6308f")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(dto.getCompany().getTicker(), is("NVDA"));
        assertThat(dto.getCompany().getCurrency(), is(Currency.$));
        assertThat(dto.getCompany().getWatching(), is(true));
        assertThat(dto.getCompany().getSector().getName(), is(Sector.SEMICONDUCTORS.getName()));
        assertThat(dto.getLatest().getPrice().getValue(), is("500"));
        assertThat(dto.getMarketCap(), is("450.39B"));
        assertThat(dto.getRecords().size(), is(2));
        assertThat(dto.getRecords().get(0).getDate(), is("05.01.2024"));
        assertThat(dto.getRecords().get(1).getDate(), is("11.11.2023"));
    }

    @Test
    void updateRecord()
    {
        String newDate = "2024-01-01";
        String newTitle = "new title";
        String newPrice = "1234.56";
        String newPe = "23.5";
        String newPs = "2";
        String newDy = "5.6";
        String newTargets = "500-400~450";
        String newContent = "[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\"saasdasdaa\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"as\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"das\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"s\"}]}]}]";
        String newStrategy = "buy as many as possible";

        RecordDto dto = new RecordDto();
        dto.setId("2ccbf4fe-dbe7-4c40-a2a2-49bf79f15dad");
        dto.setDate(newDate);
        dto.setTitle(newTitle);
        dto.setPrice(newPrice);
        dto.setPe(newPe);
        dto.setPs(newPs);
        dto.setDy(newDy);
        dto.setTargets(newTargets);
        dto.setContent(newContent);
        dto.setStrategy(newStrategy);

        Assert.put204("/record", dto);

        RecordsUiDto recordsUiDto = given().when()
                .get("/record/66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(recordsUiDto.getCompany().getTicker(), is("ABCD"));
        assertThat(recordsUiDto.getRecords().size(), is(1));
        assertThat(recordsUiDto.getRecords().get(0).getDate(), is("01.01.2024"));
        assertThat(recordsUiDto.getRecords().get(0).getTitle(), is(newTitle));
        assertThat(recordsUiDto.getRecords().get(0).getPrice(), is(newPrice));
        assertThat(recordsUiDto.getRecords().get(0).getPe(), is(newPe));
        assertThat(recordsUiDto.getRecords().get(0).getPs(), is(newPs));
        assertThat(recordsUiDto.getRecords().get(0).getDy(), is(newDy));
        assertThat(recordsUiDto.getRecords().get(0).getTargets(), is(newTargets));
        assertThat(recordsUiDto.getRecords().get(0).getContent(), is(newContent));
        assertThat(recordsUiDto.getRecords().get(0).getStrategy(), is(newStrategy));
    }

    @Test
    void updateRecordOnlyContent()
    {
        RecordsUiDto beforeRecordsUiDto = given().when()
                .get("/record/66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(beforeRecordsUiDto.getRecords().size(), is(1));

        RecordDto beforeDto = beforeRecordsUiDto.getRecords().get(0);

        String newContent = "XXXXXXXXX";
        RecordDto dto = new RecordDto();
        dto.setId(beforeDto.getId());
        dto.setContent(newContent);

        Assert.put204("/record", dto);

        RecordsUiDto afterRecordsUiDto = given().when()
                .get("/record/66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(afterRecordsUiDto.getRecords().size(), is(1));

        RecordDto afterDto = afterRecordsUiDto.getRecords().get(0);

        assertThat(afterDto.getId(), is(beforeDto.getId()));
        assertThat(afterDto.getDate(), is(beforeDto.getDate()));
        assertThat(afterDto.getTitle(), is(beforeDto.getTitle()));
        assertThat(afterDto.getPrice(), is(beforeDto.getPrice()));
        assertThat(afterDto.getPe(), is(beforeDto.getPe()));
        assertThat(afterDto.getPs(), is(beforeDto.getPs()));
        assertThat(afterDto.getDy(), is(beforeDto.getDy()));
        assertThat(afterDto.getContent(), is(newContent));
        assertThat(afterDto.getTargets(), is(beforeDto.getTargets()));
        assertThat(afterDto.getStrategy(), is(beforeDto.getStrategy()));
    }

    @Test
    void updateRecordNullableValue()
    {
        RecordsUiDto beforeRecordsUiDto = given().when()
                .get("/record/66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(beforeRecordsUiDto.getRecords().size(), is(1));

        RecordDto beforeDto = beforeRecordsUiDto.getRecords().get(0);

        RecordDto dto = new RecordDto();
        dto.setId(beforeDto.getId());
        dto.setPe("");

        given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.NO_CONTENT.getStatusCode());

        RecordsUiDto afterRecordsUiDto = given().when()
                .get("/record/66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(afterRecordsUiDto.getRecords().size(), is(1));

        RecordDto afterDto = afterRecordsUiDto.getRecords().get(0);

        assertThat(afterDto.getId(), is(beforeDto.getId()));
        assertThat(afterDto.getDate(), is(beforeDto.getDate()));
        assertThat(afterDto.getTitle(), is(beforeDto.getTitle()));
        assertThat(afterDto.getPrice(), is(beforeDto.getPrice()));
        assertThat(afterDto.getPe(), blankString());
        assertThat(afterDto.getDy(), is(beforeDto.getDy()));
        assertThat(afterDto.getContent(), is(beforeDto.getContent()));
        assertThat(afterDto.getTargets(), is(beforeDto.getTargets()));
        assertThat(afterDto.getStrategy(), is(beforeDto.getStrategy()));
    }

    @Test
    void updateRecordInvalidValues()
    {
        Assert.put400("/record", null, "Payload is NULL");

        RecordDto dto =  new RecordDto();
        Assert.put400("/record", dto, "Invalid UUID Parameter:");

        dto.setId("x");
        Assert.put400("/record", dto, "Invalid UUID Parameter:");

        dto.setId(UUID.randomUUID().toString());
        Assert.put400("/record", dto, "record with id '" + dto.getId() + "' not found");

        dto.setId("2ccbf4fe-dbe7-4c40-a2a2-49bf79f15dad");
        dto.setTitle("");
        Assert.put400("/record", dto, "Invalid Title:");

        dto.setTitle(null);
        dto.setDate("12.2020.05");
        Assert.put400("/record", dto, "Invalid Date:");

        dto.setDate("1.1.2020");
        Assert.put400("/record", dto, "Invalid Date:");

        dto.setDate(null);
        dto.setPrice("");
        Assert.put400("/record", dto, "Invalid Price:");

        dto.setPrice("x");
        Assert.put400("/record", dto, "Invalid Price:");

        dto.setPrice("1.");
        Assert.put400("/record", dto, "Invalid Price:");

        dto.setPrice(".1");
        Assert.put400("/record", dto, "Invalid Price:");

        dto.setPrice("1234567");
        Assert.put400("/record", dto, "Invalid Price:");

        dto.setPrice("10.12345");
        Assert.put400("/record", dto, "Invalid Price:");

        dto.setPrice(null);
        dto.setPe("");
        Assert.put204("/record", dto);

        dto.setPe("x");
        Assert.put400("/record", dto, "Invalid PE:");

        dto.setPe("1234");
        Assert.put400("/record", dto, "Invalid PE:");

        dto.setPe("1.123");
        Assert.put400("/record", dto, "Invalid PE:");

        dto.setPe(".1");
        Assert.put400("/record", dto, "Invalid PE:");

        dto.setPe("1.");
        Assert.put400("/record", dto, "Invalid PE:");

        dto.setPe(null);
        dto.setPs("");
        Assert.put204("/record", dto);

        dto.setPs("x");
        Assert.put400("/record", dto, "Invalid PS:");

        dto.setPs("1234");
        Assert.put400("/record", dto, "Invalid PS:");

        dto.setPs("1.123");
        Assert.put400("/record", dto, "Invalid PS:");

        dto.setPs(".1");
        Assert.put400("/record", dto, "Invalid PS:");

        dto.setPs("1.");
        Assert.put400("/record", dto, "Invalid PS:");

        dto.setPs(null);
        dto.setDy("");
        Assert.put204("/record", dto);

        dto.setDy("x");
        Assert.put400("/record", dto, "Invalid DY:");

        dto.setDy("1234");
        Assert.put400("/record", dto, "Invalid DY:");

        dto.setDy("1.123");
        Assert.put400("/record", dto, "Invalid DY:");

        dto.setDy(".1");
        Assert.put400("/record", dto, "Invalid DY:");

        dto.setDy("1.");
        Assert.put400("/record", dto, "Invalid DY:");
    }

    @Test
    void createRecord()
    {
        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId("d98c9ea1-ef2a-400a-bc7f-00d90e5d8e10");
        dto.setPrice("10.1");
        dto.setDate("2020-01-01");
        dto.setTitle("a title");

        RecordDto createdDto = given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordDto.class);

        assertThat(createdDto.getDate(), is("01.01.2020"));
        assertThat(createdDto.getTitle(), is(dto.getTitle()));
        assertThat(createdDto.getPrice(), is(dto.getPrice()));
        assertThat(createdDto.getPe(), is(blankString()));
        assertThat(createdDto.getPs(), is(blankString()));
        assertThat(createdDto.getDy(), is(blankString()));
        assertThat(createdDto.getContent(), is(nullValue()));
        assertThat(createdDto.getTargets(), is(nullValue()));
        assertThat(createdDto.getStrategy(), is(nullValue()));

        RecordsUiDto recordsUiDto = given().when()
                .get("/record/" + dto.getCompanyId())
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(recordsUiDto.getCompany().getTicker(), is("XRC"));
        assertThat(recordsUiDto.getRecords().size(), is(1));
        assertThat(recordsUiDto.getRecords().get(0).getDate(), is("01.01.2020"));
        assertThat(recordsUiDto.getRecords().get(0).getTitle(), is(dto.getTitle()));
        assertThat(recordsUiDto.getRecords().get(0).getPrice(), is(dto.getPrice()));
        assertThat(recordsUiDto.getRecords().get(0).getPe(), is(blankString()));
        assertThat(recordsUiDto.getRecords().get(0).getPs(), is(blankString()));
        assertThat(recordsUiDto.getRecords().get(0).getDy(), is(blankString()));
        assertThat(recordsUiDto.getRecords().get(0).getTargets(), is(nullValue()));
        assertThat(recordsUiDto.getRecords().get(0).getContent(), is(nullValue()));
        assertThat(recordsUiDto.getRecords().get(0).getStrategy(), is(nullValue()));
    }

    @Test
    void createRecordInvalidValues()
    {
        String validCompanyId = "d98c9ea1-ef2a-400a-bc7f-00d90e5d8e10";
        String validPrice = "10.1";
        String validDate = "2020-01-01";
        String validTitle = "a title";

        Assert.post400("/record", null, "Payload is NULL");

        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setPrice(validPrice);
        dto.setDate(validDate);

        Assert.post400("/record", dto, "Invalid Title:");

        dto.setTitle("");
        Assert.post400("/record", dto, "Invalid Title:");

        dto.setTitle(validTitle);
        dto.setDate(null);
        Assert.post400("/record", dto, "Invalid Date:");

        dto.setDate("");
        Assert.post400("/record", dto, "Invalid Date:");

        dto.setDate("01.01.2020");
        Assert.post400("/record", dto, "Invalid Date:");

        dto.setDate("2020-1-1");
        Assert.post400("/record", dto, "Invalid Date:");

        dto.setDate(validDate);
        dto.setPrice(null);
        Assert.post400("/record", dto, "Invalid Price:");

        dto.setPrice("");
        Assert.post400("/record", dto, "Invalid Price:");

        dto.setPrice("x");
        Assert.post400("/record", dto, "Invalid Price:");

        dto.setPrice("1.");
        Assert.post400("/record", dto, "Invalid Price:");

        dto.setPrice("1234567");
        Assert.post400("/record", dto, "Invalid Price:");

        dto.setPrice("10.12345");
        Assert.post400("/record", dto, "Invalid Price:");

        dto.setPrice(validPrice);
        dto.setCompanyId(null);
        Assert.post400("/record", dto, "Invalid UUID");

        dto.setCompanyId("x");
        Assert.post400("/record", dto, "Invalid UUID");

        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.post400("/record", dto, "company with id '" + dto.getCompanyId() + "' not found");
    }

    @Test
    void parameterValidator()
    {
        Assert.get400("/record/" + "AAAAAA", "Invalid UUID Parameter");
    }

    @Test
    void testLatestValues()
    {
        RecordsUiDto dto = given().when()
                .get("/record/ededb691-b3c0-4c66-b03d-4e7b46bb2489")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(dto.getCompany().getTicker(), is("XRL"));
        assertThat(dto.getRecords().size(), is(6));
        assertThat(dto.getLatest().getPrice(), is(new RecordsUiDto.Latest("100", "01.11.2022")));
        assertThat(dto.getLatest().getPe(), is(new RecordsUiDto.Latest("5", "01.10.2022")));
        assertThat(dto.getLatest().getPs(), is(new RecordsUiDto.Latest("4", "01.09.2022")));
        assertThat(dto.getLatest().getDy(), is(new RecordsUiDto.Latest("1.5", "01.08.2022")));
        assertThat(dto.getLatest().getTargets(), is(new RecordsUiDto.Latest("10-5~7", "01.07.2022")));
        assertThat(dto.getLatest().getStrategy(), is(new RecordsUiDto.Latest("strat", "01.06.2022")));
    }

    @Test
    void testFinancials()
    {
        RecordsUiDto dto = given().when()
                .get("/record/adb89a0a-86bc-4854-8a55-058ad2e6308f")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(dto.getCompany().getTicker(), is("NVDA"));
        assertThat(dto.getFinancials().getHeaders().length, is(5));
        assertThat(dto.getFinancials().getValues().size(), is(3));
        assertThat(dto.getFinancials().getValues().get(1).getQuarter(), is("23Q2"));
        assertThat(dto.getFinancials().getValues().get(1).getRevenue(), is("13.51B"));
        assertThat(dto.getFinancials().getValues().get(1).getNetIncome(), is("6.19B"));
        assertThat(dto.getFinancials().getValues().get(1).getNetMargin(), is("45.81"));
        assertThat(dto.getFinancials().getValues().get(1).getEps(), is("2.48"));
        assertThat(dto.getLatest().getPrice(), is(new RecordsUiDto.Latest("500", "05.01.2024")));
        assertThat(dto.getFinancials().getTtmLabels().length, is(6));
        assertThat(dto.getFinancials().getTtm().getRevenue(), is("51.76B"));
        assertThat(dto.getFinancials().getTtm().getNetIncome(), is("23.3B"));
        assertThat(dto.getFinancials().getTtm().getNetMargin(), is("45.01"));
        assertThat(dto.getFinancials().getTtm().getEps(), is("9.35"));
        assertThat(dto.getFinancials().getTtm().getTtmPe(), is("53.48"));
        assertThat(dto.getFinancials().getTtm().getForwardPe(), is("33.69"));
    }
}