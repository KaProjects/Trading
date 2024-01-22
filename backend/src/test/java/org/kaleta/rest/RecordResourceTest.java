package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.dto.CompanyRecordsDto;
import org.kaleta.dto.RecordCreateDto;
import org.kaleta.dto.RecordDto;
import org.kaleta.entity.Currency;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class RecordResourceTest
{

    @Test
    void getRecords()
    {
        CompanyRecordsDto dto = given().when()
                .get("/record/adb89a0a-86bc-4854-8a55-058ad2e6308f")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyRecordsDto.class);

        assertThat(dto.getTicker(), is("NVDA"));
        assertThat(dto.getCurrency(), is(Currency.$));
        assertThat(dto.getRecords().size(), is(2));
        assertThat(dto.getRecords().get(0).getDate(), is("05.01.2024"));
        assertThat(dto.getRecords().get(1).getDate(), is("11.11.2023"));
    }

    @Test
    void updateRecord()
    {
        String newDate = "01.01.2024";
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

       given().contentType(ContentType.JSON)
               .body(dto)
               .when().put("/record")
               .then().statusCode(Response.Status.NO_CONTENT.getStatusCode());

        CompanyRecordsDto companyRecordsDto = given().when()
                .get("/record/66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyRecordsDto.class);

        assertThat(companyRecordsDto.getTicker(), is("ABCD"));
        assertThat(companyRecordsDto.getRecords().size(), is(1));
        assertThat(companyRecordsDto.getRecords().get(0).getDate(), is(newDate));
        assertThat(companyRecordsDto.getRecords().get(0).getTitle(), is(newTitle));
        assertThat(companyRecordsDto.getRecords().get(0).getPrice(), is(newPrice));
        assertThat(companyRecordsDto.getRecords().get(0).getPe(), is(newPe));
        assertThat(companyRecordsDto.getRecords().get(0).getPs(), is(newPs));
        assertThat(companyRecordsDto.getRecords().get(0).getDy(), is(newDy));
        assertThat(companyRecordsDto.getRecords().get(0).getTargets(), is(newTargets));
        assertThat(companyRecordsDto.getRecords().get(0).getContent(), is(newContent));
        assertThat(companyRecordsDto.getRecords().get(0).getStrategy(), is(newStrategy));
    }

    @Test
    void updateRecordOnlyContent()
    {
        CompanyRecordsDto beforeCompanyRecordsDto = given().when()
                .get("/record/66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyRecordsDto.class);

        assertThat(beforeCompanyRecordsDto.getRecords().size(), is(1));

        RecordDto beforeDto = beforeCompanyRecordsDto.getRecords().get(0);

        String newContent = "XXXXXXXXX";
        RecordDto dto = new RecordDto();
        dto.setId(beforeDto.getId());
        dto.setContent(newContent);

        given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.NO_CONTENT.getStatusCode());

        CompanyRecordsDto afterCompanyRecordsDto = given().when()
                .get("/record/66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyRecordsDto.class);

        assertThat(afterCompanyRecordsDto.getRecords().size(), is(1));

        RecordDto afterDto = afterCompanyRecordsDto.getRecords().get(0);

        assertThat(afterDto.getId(), is(beforeDto.getId()));
        assertThat(afterDto.getDate(), is(beforeDto.getDate()));
        assertThat(afterDto.getTitle(), is(beforeDto.getTitle()));
        assertThat(afterDto.getPrice(), is(beforeDto.getPrice()));
        assertThat(afterDto.getPe(), is(beforeDto.getPe()));
        assertThat(afterDto.getDy(), is(beforeDto.getDy()));
        assertThat(afterDto.getContent(), is(newContent));
        assertThat(afterDto.getTargets(), is(beforeDto.getTargets()));
        assertThat(afterDto.getStrategy(), is(beforeDto.getStrategy()));
    }

    @Test
    void updateRecordNullableValue()
    {
        CompanyRecordsDto beforeCompanyRecordsDto = given().when()
                .get("/record/66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyRecordsDto.class);

        assertThat(beforeCompanyRecordsDto.getRecords().size(), is(1));

        RecordDto beforeDto = beforeCompanyRecordsDto.getRecords().get(0);

        RecordDto dto = new RecordDto();
        dto.setId(beforeDto.getId());
        dto.setPe("");

        given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.NO_CONTENT.getStatusCode());

        CompanyRecordsDto afterCompanyRecordsDto = given().when()
                .get("/record/66c725b2-9987-4653-a49c-3a9906168d2a")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyRecordsDto.class);

        assertThat(afterCompanyRecordsDto.getRecords().size(), is(1));

        RecordDto afterDto = afterCompanyRecordsDto.getRecords().get(0);

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
        RecordDto dto = new RecordDto();
        dto.setId("2ccbf4fe-dbe7-4c40-a2a2-49bf79f15dad");

        dto.setTitle("");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Title:"));

        dto.setTitle(null);
        dto.setDate("12.2020.05");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Date:"));

        dto.setDate("1.1.2020");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Date:"));

        dto.setDate(null);
        dto.setPrice("");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice("x");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice("1.");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice("12345678901");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice("10.12345");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice(null);
        dto.setPe("");

        given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.NO_CONTENT.getStatusCode());

        dto.setPe("x");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid PE:"));

        dto.setPe("123456");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid PE:"));

        dto.setPe("1.123");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid PE:"));

        dto.setPe(null);
        dto.setPs("");

        given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.NO_CONTENT.getStatusCode());

        dto.setPs("x");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid PS:"));

        dto.setPs("123456");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid PS:"));

        dto.setPs("1.123");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid PS:"));

        dto.setPs(null);
        dto.setDy("");

        given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.NO_CONTENT.getStatusCode());

        dto.setDy("x");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid DY:"));

        dto.setDy("123456");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid DY:"));

        dto.setDy("1.123");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().put("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid DY:"));
    }

    @Test
    void createRecord()
    {
        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId("d98c9ea1-ef2a-400a-bc7f-00d90e5d8e10");
        dto.setPrice("10.1");
        dto.setDate("01.01.2020");
        dto.setTitle("a title");

        RecordDto createdDto = given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordDto.class);

        assertThat(createdDto.getDate(), is(dto.getDate()));
        assertThat(createdDto.getTitle(), is(dto.getTitle()));
        assertThat(createdDto.getPrice(), is(dto.getPrice()));
        assertThat(createdDto.getPe(), is(blankString()));
        assertThat(createdDto.getDy(), is(blankString()));
        assertThat(createdDto.getContent(), is(nullValue()));
        assertThat(createdDto.getTargets(), is(nullValue()));
        assertThat(createdDto.getStrategy(), is(nullValue()));

        CompanyRecordsDto companyRecordsDto = given().when()
                .get("/record/" + dto.getCompanyId())
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", CompanyRecordsDto.class);

        assertThat(companyRecordsDto.getTicker(), is("XRC"));
        assertThat(companyRecordsDto.getRecords().size(), is(1));
        assertThat(companyRecordsDto.getRecords().get(0).getDate(), is(dto.getDate()));
        assertThat(companyRecordsDto.getRecords().get(0).getTitle(), is(dto.getTitle()));
        assertThat(companyRecordsDto.getRecords().get(0).getPrice(), is(dto.getPrice()));
        assertThat(companyRecordsDto.getRecords().get(0).getPe(), is(blankString()));
        assertThat(companyRecordsDto.getRecords().get(0).getDy(), is(blankString()));
        assertThat(companyRecordsDto.getRecords().get(0).getTargets(), is(nullValue()));
        assertThat(companyRecordsDto.getRecords().get(0).getContent(), is(nullValue()));
        assertThat(companyRecordsDto.getRecords().get(0).getStrategy(), is(nullValue()));
    }

    @Test
    void createRecordInvalidValues()
    {
        String validCompanyId = "d98c9ea1-ef2a-400a-bc7f-00d90e5d8e10";
        String validPrice = "10.1";
        String validDate = "01.01.2020";
        String validTitle = "a title";

        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setPrice(validPrice);
        dto.setDate(validDate);

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Title:"));

        dto.setTitle("");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Title:"));

        dto.setTitle(validTitle);
        dto.setDate(null);

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Date:"));

        dto.setDate("");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Date:"));

        dto.setDate("1.1.2020");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Date:"));

        dto.setDate(validDate);
        dto.setPrice(null);

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice("");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice("x");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice("1.");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice("12345678901");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice("10.12345");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid Price:"));

        dto.setPrice(validPrice);
        dto.setCompanyId(null);

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid UUID"));

        dto.setCompanyId("x");

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid UUID"));

        String randomUuid = UUID.randomUUID().toString();
        dto.setCompanyId(randomUuid);

        assertThat(given().contentType(ContentType.JSON)
                .body(dto)
                .when().post("/record")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), is("company with id '" + randomUuid + "' not found"));
    }

    @Test
    void parameterValidator()
    {
        assertThat(given().when()
                .get("/record/" + "AAAAAA")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Invalid UUID Parameter"));

        assertThat(given().when()
                .put("/record")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Payload is NULL"));

        RecordDto dto =  new RecordDto();
        assertThat(given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .put("/record")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), is("Invalid UUID Parameter: 'null'"));

        dto.setId(UUID.randomUUID().toString());
        assertThat(given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .put("/record")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), is("record with id '" + dto.getId() + "' not found"));

        assertThat(given()
                .contentType(ContentType.JSON)
                .when()
                .post("/record")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString("Payload is NULL"));
    }
}