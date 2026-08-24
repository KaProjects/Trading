package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.api.TargetDao;
import org.kaleta.persistence.entity.Target;
import org.kaleta.rest.dto.TargetCreateDto;
import org.kaleta.rest.dto.TargetDto;
import org.kaleta.rest.dto.TargetSyncCountsDto;
import org.kaleta.rest.dto.TargetSyncDto;
import org.kaleta.service.FirebaseService;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.kaleta.framework.Assert.ExpectedViolation.MATCH_DATE_FORMAT;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.kaleta.framework.Assert.delete400;
import static org.kaleta.framework.Assert.deleteValidationError;
import static org.kaleta.framework.Assert.get400;
import static org.kaleta.framework.Assert.getValidationError;
import static org.kaleta.framework.Assert.postValidationError;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@QuarkusTest
class TargetEndpointsTest
{
    private static final String PATH = "/target";
    private static final Long REPORTED_PERIOD_ID = 1837L;
    private static final Long UNREPORTED_PERIOD_ID = 2182L;
    private static final Long COMPANY_ID = 2281L;

    @Inject
    TargetDao targetDao;
    @Inject
    PeriodDao periodDao;

    @InjectMock
    @MockitoConfig(convertScopes = true)
    FirebaseService firebaseService;

    @BeforeEach
    void before()
    {
        reset(firebaseService);
        when(firebaseService.getTargets("RCH"))
                .thenReturn(new FirebaseService.TargetsResult(List.of(), List.of()));
    }

    @Test
    void getAll()
    {
        List<TargetDto> targets = given().when()
                .get(PATH + "/" + REPORTED_PERIOD_ID)
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .contentType(ContentType.JSON)
                .extract().jsonPath().getList("", TargetDto.class);

        assertThat(targets, hasSize(3));
        assertThat(targets.get(0).getInstitution(), is("Gamma Markets"));
        assertThat(targets.get(0).getDate(), is("2025-02-14"));
        assertBigDecimals(targets.get(0).getPrice(), new BigDecimal("175"));
        assertThat(targets.get(1).getTakeaway2(), is("Valuation remains reasonable."));
    }

    @Test
    void getAll_invalidParameters()
    {
        getValidationError(PATH + "/0", VALID_ID);
        get400(PATH + "/4294967295", "period with id '4294967295' not found");
    }

    @Test
    void create()
    {
        TargetDto created = given()
                .contentType(ContentType.JSON)
                .body(dto("2025-03-11", "Endpoint Capital", "166.7500"))
                .when().post(PATH + "/" + UNREPORTED_PERIOD_ID)
                .then().log().ifError()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract().as(TargetDto.class);

        Target entity = targetDao.get(created.getId());
        assertThat(entity.getPeriod().getId(), is(UNREPORTED_PERIOD_ID));
        assertThat(entity.getInstitution(), is("Endpoint Capital"));
        assertBigDecimals(entity.getPrice(), new BigDecimal("166.7500"));

        given().contentType(ContentType.JSON)
                .body(dto("2025-03-11", "Endpoint Capital", "166.7500"))
                .when().post(PATH + "/" + UNREPORTED_PERIOD_ID)
                .then()
                .statusCode(Response.Status.CONFLICT.getStatusCode())
                .body(containsString("target already exists"));

        targetDao.delete(created.getId());
    }

    @Test
    void create_invalidParameters()
    {
        postValidationError(PATH + "/0", dto("2025-03-11", "Institution", "100"), VALID_ID);
        postValidationError(PATH + "/" + UNREPORTED_PERIOD_ID, null, NOT_NULL);

        TargetCreateDto dto = dto("invalid", " ", "0");
        postValidationError(
                PATH + "/" + UNREPORTED_PERIOD_ID,
                dto,
                MATCH_DATE_FORMAT,
                "must not be blank",
                "must be greater than 0");

        TargetCreateDto missingRequired = dto("2025-03-11", "Institution", "100");
        missingRequired.setDate(null);
        missingRequired.setInstitution(null);
        missingRequired.setPrice(null);
        postValidationError(
                PATH + "/" + UNREPORTED_PERIOD_ID,
                missingRequired,
                NOT_NULL,
                "must not be blank",
                NOT_NULL);

        TargetCreateDto oversized = dto("2025-03-11", "I".repeat(51), "100");
        oversized.setRating("R".repeat(31));
        oversized.setOverview("O".repeat(1001));
        oversized.setTakeaway1("T".repeat(501));
        oversized.setTakeaway2("T".repeat(501));
        oversized.setTakeaway3("T".repeat(501));
        oversized.setTakeaway4("T".repeat(501));
        postValidationError(
                PATH + "/" + UNREPORTED_PERIOD_ID,
                oversized,
                "size must be between 0 and 50",
                "size must be between 0 and 30",
                "size must be between 0 and 1000",
                "size must be between 0 and 500",
                "size must be between 0 and 500",
                "size must be between 0 and 500",
                "size must be between 0 and 500");

        given().contentType(ContentType.JSON)
                .body(dto("2025-02-14", "Out of range", "100"))
                .when().post(PATH + "/" + UNREPORTED_PERIOD_ID)
                .then().log().ifError()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString("must be on or after '2025-02-15' and before '2025-05-15'"));
    }

    @Test
    void delete()
    {
        Target target = target(UNREPORTED_PERIOD_ID, "2025-03-12", "Delete Capital", "167");
        targetDao.create(target);

        given().when().delete(PATH + "/" + target.getId())
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode());

        delete400(PATH + "/" + target.getId(), "target with id '" + target.getId() + "' not found");
    }

    @Test
    void delete_invalidParameters()
    {
        deleteValidationError(PATH + "/0", VALID_ID);
        delete400(PATH + "/4294967295", "target with id '4294967295' not found");
    }

    @Test
    void countImportCandidates()
    {
        when(firebaseService.getTargets("RCH")).thenReturn(new FirebaseService.TargetsResult(List.of(
                firebaseTarget("2025-03-20", "Sync Count Capital", "180")
        ), List.of()));

        TargetSyncDto result = given().when()
                .get(PATH + "/" + UNREPORTED_PERIOD_ID + "/sync/count")
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().as(TargetSyncDto.class);

        assertThat(result.count(), is(1));
        assertThat(result.warnings(), is(List.of()));
    }

    @Test
    void countImportCandidatesByCompany()
    {
        when(firebaseService.getTargets("RCH")).thenReturn(new FirebaseService.TargetsResult(List.of(
                firebaseTarget("2025-01-20", "Reported Period Capital", "180"),
                firebaseTarget("2025-03-20", "Unreported Period Capital", "181")
        ), List.of()));

        TargetSyncCountsDto result = given().when()
                .get(PATH + "/company/" + COMPANY_ID + "/sync/counts")
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().as(TargetSyncCountsDto.class);

        assertThat(result.counts(), is(Map.of(
                1338L, 0,
                REPORTED_PERIOD_ID, 1,
                UNREPORTED_PERIOD_ID, 1)));
        assertThat(result.failedPeriodIds(), is(Set.of()));
        assertThat(result.warnings(), is(List.of()));
    }

    @Test
    void countImportCandidatesByCompany_externalFailureMarksEveryPeriod()
    {
        when(firebaseService.getTargets("RCH")).thenReturn(new FirebaseService.TargetsResult(
                List.of(),
                List.of("Firebase targets for RCH could not be loaded: permission denied")));

        TargetSyncCountsDto result = given().when()
                .get(PATH + "/company/" + COMPANY_ID + "/sync/counts")
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().as(TargetSyncCountsDto.class);

        assertThat(result.failedPeriodIds(), is(Set.of(1338L, REPORTED_PERIOD_ID, UNREPORTED_PERIOD_ID)));
        assertThat(result.warnings(), contains("Firebase targets for RCH could not be loaded: permission denied"));
    }

    @Test
    void countImportCandidatesByCompany_invalidParameters()
    {
        getValidationError(PATH + "/company/0/sync/counts", VALID_ID);
        get400(PATH + "/company/4294967295/sync/counts", "company with id '4294967295' not found");
    }

    @Test
    void countImportCandidates_externalFailureReturnsWarning()
    {
        when(firebaseService.getTargets("RCH")).thenReturn(new FirebaseService.TargetsResult(
                List.of(),
                List.of("Firebase targets for RCH could not be loaded: permission denied")));

        TargetSyncDto result = given().when()
                .get(PATH + "/" + UNREPORTED_PERIOD_ID + "/sync/count")
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().as(TargetSyncDto.class);

        assertThat(result.count(), is(0));
        assertThat(result.warnings(), contains("Firebase targets for RCH could not be loaded: permission denied"));
    }

    @Test
    void countImportCandidates_invalidParameters()
    {
        getValidationError(PATH + "/0/sync/count", VALID_ID);
        get400(PATH + "/4294967295/sync/count", "period with id '4294967295' not found");
    }

    @Test
    void sync()
    {
        when(firebaseService.getTargets("RCH")).thenReturn(new FirebaseService.TargetsResult(List.of(
                firebaseTarget("2025-03-21", "Sync Capital", "181.25")
        ), List.of()));

        TargetSyncDto result = given().when()
                .post(PATH + "/" + UNREPORTED_PERIOD_ID + "/sync")
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().as(TargetSyncDto.class);

        assertThat(result.count(), is(1));
        Target created = targetDao.findByIdentity(
                        UNREPORTED_PERIOD_ID,
                        Date.valueOf("2025-03-21"),
                        "Sync Capital",
                        new BigDecimal("181.25"))
                .orElseThrow();
        assertThat(created.getRating(), is("Buy"));

        TargetSyncDto secondSync = given().when()
                .post(PATH + "/" + UNREPORTED_PERIOD_ID + "/sync")
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().as(TargetSyncDto.class);
        assertThat(secondSync.count(), is(0));

        targetDao.delete(created.getId());
    }

    @Test
    void sync_invalidParameters()
    {
        postValidationError(PATH + "/0/sync", null, VALID_ID);
        given().when().post(PATH + "/4294967295/sync")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString("period with id '4294967295' not found"));
    }

    private TargetCreateDto dto(String date, String institution, String price)
    {
        TargetCreateDto dto = new TargetCreateDto();
        dto.setDate(date);
        dto.setInstitution(institution);
        dto.setPrice(price);
        dto.setRating("Buy");
        dto.setOverview("Endpoint overview");
        dto.setTakeaway1("Endpoint takeaway");
        return dto;
    }

    private FirebaseCompany.Gemini.Target firebaseTarget(String date, String institution, String price)
    {
        FirebaseCompany.Gemini.Target target = new FirebaseCompany.Gemini.Target();
        target.setDate(date);
        target.setInstitution(institution);
        target.setPrice(price);
        target.setRating("Buy");
        return target;
    }

    private Target target(Long periodId, String date, String institution, String price)
    {
        Target target = new Target();
        target.setPeriod(periodDao.get(periodId));
        target.setDate(Date.valueOf(date));
        target.setInstitution(institution);
        target.setPrice(new BigDecimal(price));
        return target;
    }
}
