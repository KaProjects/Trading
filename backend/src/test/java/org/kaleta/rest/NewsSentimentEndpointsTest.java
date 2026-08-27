package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.rest.dto.NewsSentimentLatestDto;
import org.kaleta.rest.dto.NewsSentimentPeriodDto;
import org.kaleta.service.FirebaseService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;
import static org.kaleta.framework.Assert.get400;
import static org.kaleta.framework.Assert.getValidationError;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@QuarkusTest
class NewsSentimentEndpointsTest
{
    private static final String PATH = "/news-sentiment";
    private static final Long COMPANY_ID = 2281L;
    private static final Long PERIOD_ID = 1837L;

    @InjectMock
    @MockitoConfig(convertScopes = true)
    FirebaseService firebaseService;

    @BeforeEach
    void before()
    {
        reset(firebaseService);
        when(firebaseService.getLatestNewsSentiments("RCH"))
                .thenReturn(new FirebaseService.NewsSentimentsResult(Map.of(), List.of()));
        when(firebaseService.getNewsSentiments(
                "RCH",
                java.time.LocalDate.parse("2024-11-15"),
                java.time.LocalDate.parse("2025-02-15")))
                .thenReturn(new FirebaseService.NewsSentimentsResult(Map.of(), List.of()));
    }

    @Test
    void getLatest()
    {
        when(firebaseService.getLatestNewsSentiments("RCH"))
                .thenReturn(new FirebaseService.NewsSentimentsResult(
                        Map.of("2025-02-09-latest", sentiment(Map.of("positive", 2, "neutral", 1), "Demand improved.")),
                        List.of()));

        NewsSentimentLatestDto result = given().when()
                .get(PATH + "/company/" + COMPANY_ID + "/latest")
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .contentType(ContentType.JSON)
                .extract().as(NewsSentimentLatestDto.class);

        assertThat(result.record().date().toString(), is("2025-02-09"));
        assertThat(result.record().total(), is(3));
        assertThat(result.record().stats(), is(Map.of("positive", 2, "neutral", 1)));
        assertThat(result.record().keyTakeaways(), contains("Demand improved."));
    }

    @Test
    void getByPeriodUsesHalfOpenDateWindow()
    {
        Map<String, FirebaseCompany.NewsSentiment> records = new LinkedHashMap<>();
        records.put("2024-11-15-start", sentiment(Map.of("positive", 1), "Start"));
        records.put("2025-02-14-last", sentiment(Map.of("mixed", 2), "Last"));
        records.put("2025-02-15-end", sentiment(Map.of("negative", 1), "End"));
        when(firebaseService.getNewsSentiments(
                "RCH",
                java.time.LocalDate.parse("2024-11-15"),
                java.time.LocalDate.parse("2025-02-15")))
                .thenReturn(new FirebaseService.NewsSentimentsResult(records, List.of()));

        NewsSentimentPeriodDto result = given().when()
                .get(PATH + "/period/" + PERIOD_ID)
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .contentType(ContentType.JSON)
                .extract().as(NewsSentimentPeriodDto.class);

        assertThat(result.records(), hasSize(2));
        assertThat(result.records().getFirst().id(), is("2025-02-14-last"));
        assertThat(result.window().start().toString(), is("2024-11-15"));
        assertThat(result.window().end().toString(), is("2025-02-15"));
    }

    @Test
    void externalFailureReturnsHttp200WithWarning()
    {
        when(firebaseService.getLatestNewsSentiments("RCH"))
                .thenReturn(new FirebaseService.NewsSentimentsResult(
                        Map.of(),
                        List.of("Firebase news sentiment for RCH could not be loaded: permission denied")));

        NewsSentimentLatestDto result = given().when()
                .get(PATH + "/company/" + COMPANY_ID + "/latest")
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().as(NewsSentimentLatestDto.class);

        assertThat(result.warnings(), contains(
                "Firebase news sentiment for RCH could not be loaded: permission denied"));
    }

    @Test
    void invalidParameters()
    {
        getValidationError(PATH + "/company/0/latest", VALID_ID);
        getValidationError(PATH + "/period/0", VALID_ID);
        get400(PATH + "/company/4294967295/latest", "company with id '4294967295' not found");
        get400(PATH + "/period/4294967295", "period with id '4294967295' not found");
    }

    private FirebaseCompany.NewsSentiment sentiment(Map<String, Integer> values, String... takeaways)
    {
        FirebaseCompany.NewsSentiment sentiment = new FirebaseCompany.NewsSentiment();
        sentiment.setStats(values);
        sentiment.setKey_takeaways(List.of(takeaways));
        return sentiment;
    }
}
