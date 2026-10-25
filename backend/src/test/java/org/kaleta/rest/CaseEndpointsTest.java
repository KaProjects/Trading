package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.rest.dto.CaseCreateDto;
import org.kaleta.rest.dto.CaseDto;
import org.kaleta.rest.dto.CaseUpdateDto;

import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;
import static org.kaleta.framework.Assert.getValidationError;
import static org.kaleta.framework.Assert.postValidationError;

@QuarkusTest
class CaseEndpointsTest
{
    private static final String PATH = "/case";
    private static final Long RCH = 2281L;
    private static final Long UPD = 1842L;

    @Test
    void createReadsBackWithItsCompaniesAndOpensToday()
    {
        CaseDto created = create("memory supercycle", List.of(RCH, UPD));

        assertThat(created.getId(), is(notNullValue()));
        assertThat(created.getName(), is("memory supercycle"));
        assertThat(created.getCreatedAt().toLocalDate(), is(LocalDate.now()));
        assertThat(created.getClosedAt(), is(nullValue()));
        assertThat(created.getCompanies().stream().map(CaseDto.Company::getTicker).toList(),
                contains("RCH", "UPD"));

        CaseDto read = get(created.getId());
        assertThat(read.getName(), is("memory supercycle"));
        assertThat(read.getCompanies().size(), is(2));

        delete(created.getId());
    }

    @Test
    void namesTheCaseAgainAndChangesWhichCompaniesItCovers()
    {
        CaseDto created = create("renamed later", List.of(RCH));

        CaseUpdateDto dto = new CaseUpdateDto();
        dto.setName("the better name");
        dto.setCompanyIds(List.of(UPD));
        CaseDto updated = update(created.getId(), dto);

        assertThat(updated.getName(), is("the better name"));
        assertThat(updated.getCompanies().stream().map(CaseDto.Company::getTicker).toList(),
                contains("UPD"));

        delete(created.getId());
    }

    @Test
    void closesTheCaseAndOpensItAgain()
    {
        CaseDto created = create("closed and reopened", List.of());

        CaseUpdateDto close = new CaseUpdateDto();
        close.setClosed(true);
        assertThat(update(created.getId(), close).getClosedAt().toLocalDate(), is(LocalDate.now()));

        CaseUpdateDto open = new CaseUpdateDto();
        open.setClosed(false);
        assertThat(update(created.getId(), open).getClosedAt(), is(nullValue()));

        delete(created.getId());
    }

    @Test
    void listsOpenCasesBeforeClosedOnes()
    {
        CaseDto open = create("still running", List.of());
        CaseDto closed = create("done with", List.of());
        CaseUpdateDto close = new CaseUpdateDto();
        close.setClosed(true);
        update(closed.getId(), close);

        List<Long> ids = list().stream().map(CaseDto::getId).toList();
        assertThat(ids.indexOf(open.getId()) < ids.indexOf(closed.getId()), is(true));

        delete(open.getId());
        delete(closed.getId());
    }

    @Test
    void keepsTheSameCompanyOnlyOnce()
    {
        CaseDto created = create("one company twice", List.of(RCH, RCH));

        assertThat(created.getCompanies().size(), is(1));

        delete(created.getId());
    }

    @Test
    void refusesACaseWithoutANameOrWithACompanyThatDoesNotExist()
    {
        CaseCreateDto blank = new CaseCreateDto();
        blank.setName(" ");
        postValidationError(PATH, blank, "must not be blank");

        CaseCreateDto missingCompany = new CaseCreateDto();
        missingCompany.setName("with a ghost");
        missingCompany.setCompanyIds(List.of(999999L));
        given().contentType(ContentType.JSON).body(missingCompany)
                .when().post(PATH)
                .then().statusCode(400);
    }

    @Test
    void refusesAnIdThatIsNotOne()
    {
        getValidationError(PATH + "/0", VALID_ID);
        given().when().get(PATH + "/999999").then().statusCode(400);
    }

    @Test
    void listsCasesWithoutCompaniesAsWell()
    {
        CaseDto created = create("no companies yet", List.of());

        assertThat(created.getCompanies(), is(empty()));

        delete(created.getId());
    }

    private CaseDto create(String name, List<Long> companyIds)
    {
        CaseCreateDto dto = new CaseCreateDto();
        dto.setName(name);
        dto.setCompanyIds(companyIds);
        return given().contentType(ContentType.JSON).body(dto)
                .when().post(PATH)
                .then().log().ifError().statusCode(201)
                .extract().response().jsonPath().getObject("", CaseDto.class);
    }

    private CaseDto get(Long caseId)
    {
        return given().when().get(PATH + "/" + caseId)
                .then().statusCode(200)
                .extract().response().jsonPath().getObject("", CaseDto.class);
    }

    private List<CaseDto> list()
    {
        return given().when().get(PATH)
                .then().statusCode(200)
                .extract().response().jsonPath().getList("", CaseDto.class);
    }

    private CaseDto update(Long caseId, CaseUpdateDto dto)
    {
        return given().contentType(ContentType.JSON).body(dto)
                .when().put(PATH + "/" + caseId)
                .then().log().ifError().statusCode(200)
                .extract().response().jsonPath().getObject("", CaseDto.class);
    }

    private void delete(Long caseId)
    {
        given().when().delete(PATH + "/" + caseId).then().statusCode(200);
    }
}
