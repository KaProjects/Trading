package org.kaleta.framework;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class Assert
{
    public static void post400(String uri, Object dto, String expectedMessage)
    {
        RequestSpecification rs = given().contentType(ContentType.JSON);
        if (dto != null) rs = rs.body(dto);
        String responseBody = rs.when()
                .post(uri)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString();
        assertThat(responseBody, containsString(expectedMessage));
    }

    public static void get400(String uri, String expectedError)
    {
        assertThat(given().when()
                .get(uri)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().body().asString(), containsString(expectedError));
    }

    public static void delete400(String uri, String expectedError)
    {
        assertThat(given().when()
                .delete(uri)
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
                .then().log().ifError()
                .statusCode(Response.Status.NO_CONTENT.getStatusCode());
    }

    public static void post201(String uri, Object dto)
    {
        given().contentType(ContentType.JSON)
                .body(dto)
                .when().post(uri)
                .then().log().ifError()
                .statusCode(Response.Status.CREATED.getStatusCode());
    }

    public static void delete200(String uri)
    {
        given().when().delete(uri)
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode());
    }

    public static void postValidationError(String uri, Object dto, String... expectedViolations)
    {
        RequestSpecification rs = given().contentType(ContentType.JSON);
        if (dto != null) rs = rs.body(dto);
        assertValidationErrorResponse(rs.when().post(uri), expectedViolations);
    }

    public static void putValidationError(String uri, Object dto, String... expectedViolations)
    {
        RequestSpecification rs = given().contentType(ContentType.JSON);
        if (dto != null) rs = rs.body(dto);
        assertValidationErrorResponse(rs.when().put(uri), expectedViolations);
    }

    public static void getValidationError(String uri, String... expectedViolations)
    {
        RequestSpecification rs = given().contentType(ContentType.JSON);
        assertValidationErrorResponse(rs.when().get(uri), expectedViolations);
    }

    public static void deleteValidationError(String uri, String... expectedViolations)
    {
        RequestSpecification rs = given().contentType(ContentType.JSON);
        assertValidationErrorResponse(rs.when().delete(uri), expectedViolations);
    }

    public static void assertBigDecimals(BigDecimal expected, BigDecimal actual)
    {
        if (expected == null){
            assertThat(actual, is(nullValue()));
        } else {
            assertThat(actual, comparesEqualTo(expected));
        }
    }

    private static void assertValidationErrorResponse(io.restassured.response.Response response, String... expectedViolations)
    {
        ValidationErrorResponse validationErrorResponse = response.then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().as(ValidationErrorResponse.class);
        assertThat(validationErrorResponse.getTitle(), is("Constraint Violation"));
        assertThat(validationErrorResponse.getStatus(), is(400));
        assertThat(validationErrorResponse.getViolations().toString(),validationErrorResponse.getViolations().size(), is(expectedViolations.length));
        for (int i = 0; i < expectedViolations.length; i++) {
            assertThat(validationErrorResponse.getViolations().get(i).getMessage(), is(expectedViolations[i]));
        }
    }
}
