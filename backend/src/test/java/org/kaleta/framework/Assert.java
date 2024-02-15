package org.kaleta.framework;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.CompanyDto;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class Assert
{
    public static void post400(String uri, Object dto, String expectedError)
    {
        RequestSpecification rs = given().contentType(ContentType.JSON);
        if (dto != null) rs = rs.body(dto);
        String responseBody = rs.when()
                .post(uri)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString();
        assertThat(responseBody, containsString(expectedError));
    }

    public static void get400(String uri, String expectedError)
    {
        assertThat(given().when()
                .get(uri)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString(expectedError));
    }

    public static void put400(String uri, Object dto, String expectedError)
    {
        RequestSpecification rs = given().contentType(ContentType.JSON);
        if (dto != null) rs = rs.body(dto);
        String responseBody = rs.when()
                .put(uri)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString();
        assertThat(responseBody, containsString(expectedError));
    }

    public static void put204(String uri, Object dto)
    {
        given().contentType(ContentType.JSON)
                .body(dto)
                .when().put(uri)
                .then().statusCode(Response.Status.NO_CONTENT.getStatusCode());
    }

    public static void post201(String uri, Object dto)
    {
        given().contentType(ContentType.JSON)
                .body(dto)
                .when().post(uri)
                .then().statusCode(Response.Status.CREATED.getStatusCode());
    }
}
