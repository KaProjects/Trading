package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.rest.dto.CaseCreateDto;
import org.kaleta.rest.dto.CaseDto;
import org.kaleta.rest.dto.CaseEntryUpdateDto;
import org.kaleta.rest.dto.CaseUpdateDto;

import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
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

        // Read again rather than trusting the answer: a column the mapping refuses to write
        // still answers with what was asked for.
        CaseDto read = get(created.getId());
        assertThat(read.getName(), is("the better name"));
        assertThat(read.getCompanies().stream().map(CaseDto.Company::getTicker).toList(),
                contains("UPD"));

        delete(created.getId());
    }

    @Test
    void keepsCompaniesTheAppDoesNotHoldAsTheNamesTheyWereWrittenAs()
    {
        CaseCreateDto dto = new CaseCreateDto();
        dto.setName("written companies");
        dto.setCompanyIds(List.of(RCH));
        dto.setCompanyNames(List.of("  Broadcom ", "TSMC", "broadcom", "", "  "));
        CaseDto created = given().contentType(ContentType.JSON).body(dto)
                .when().post(PATH)
                .then().log().ifError().statusCode(201)
                .extract().response().jsonPath().getObject("", CaseDto.class);

        assertThat(created.getCompanies().stream().map(CaseDto.Company::getTicker).toList(), contains("RCH"));
        assertThat(created.getCompanyNames(), contains("Broadcom", "TSMC"));

        CaseUpdateDto update = new CaseUpdateDto();
        update.setCompanyNames(List.of("Samsung"));
        CaseDto updated = update(created.getId(), update);
        assertThat(updated.getCompanyNames(), contains("Samsung"));
        assertThat(updated.getCompanies().stream().map(CaseDto.Company::getTicker).toList(), contains("RCH"));
        assertThat(get(created.getId()).getCompanyNames(), contains("Samsung"));

        CaseUpdateDto tooLong = new CaseUpdateDto();
        tooLong.setCompanyNames(List.of("x".repeat(51)));
        given().contentType(ContentType.JSON).body(tooLong)
                .when().put(PATH + "/" + created.getId())
                .then().statusCode(400)
                .body(containsString("at most 50 characters"));

        delete(created.getId());
    }

    @Test
    void opensTheCaseOnAnotherDay()
    {
        CaseDto created = create("opened earlier than written down", List.of());

        CaseUpdateDto dto = new CaseUpdateDto();
        dto.setCreatedAt("2026-02-10");
        assertThat(update(created.getId(), dto).getCreatedAt().toLocalDate(),
                is(LocalDate.of(2026, 2, 10)));
        // Read back rather than taken from the answer: the day has to be in the database.
        assertThat(get(created.getId()).getCreatedAt().toLocalDate(),
                is(LocalDate.of(2026, 2, 10)));

        CaseUpdateDto close = new CaseUpdateDto();
        close.setClosed(true);
        update(created.getId(), close);

        CaseUpdateDto afterClosing = new CaseUpdateDto();
        afterClosing.setCreatedAt(LocalDate.now().plusDays(1).toString());
        given().contentType(ContentType.JSON).body(afterClosing)
                .when().put(PATH + "/" + created.getId())
                .then().statusCode(400)
                .body(containsString("cannot open after it was closed"));

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
        assertThat(get(created.getId()).getClosedAt(), is(nullValue()));

        delete(created.getId());
    }

    @Test
    void closesTheCaseOnAnotherDay()
    {
        CaseDto created = create("closed later than written down", List.of());

        CaseUpdateDto whileOpen = new CaseUpdateDto();
        whileOpen.setClosedAt("2026-02-10");
        given().contentType(ContentType.JSON).body(whileOpen)
                .when().put(PATH + "/" + created.getId())
                .then().statusCode(400)
                .body(containsString("is still open"));

        CaseUpdateDto opened = new CaseUpdateDto();
        opened.setCreatedAt("2026-01-01");
        update(created.getId(), opened);

        CaseUpdateDto close = new CaseUpdateDto();
        close.setClosed(true);
        assertThat(update(created.getId(), close).getClosedAt().toLocalDate(), is(LocalDate.now()));

        // Closed today by the menu, put right afterwards to the day it was really closed.
        CaseUpdateDto closedOn = new CaseUpdateDto();
        closedOn.setClosedAt("2026-06-01");
        assertThat(update(created.getId(), closedOn).getClosedAt().toLocalDate(),
                is(LocalDate.of(2026, 6, 1)));
        assertThat(get(created.getId()).getClosedAt().toLocalDate(), is(LocalDate.of(2026, 6, 1)));

        // The day it opened is left where it was: each day is changed on its own.
        assertThat(get(created.getId()).getCreatedAt().toLocalDate(), is(LocalDate.of(2026, 1, 1)));

        CaseUpdateDto beforeOpening = new CaseUpdateDto();
        beforeOpening.setClosedAt("2020-01-01");
        given().contentType(ContentType.JSON).body(beforeOpening)
                .when().put(PATH + "/" + created.getId())
                .then().statusCode(400)
                .body(containsString("cannot close before it was opened"));

        given().when().delete(PATH + "/" + created.getId()).then().statusCode(200);
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
    void refusesToChangeACaseToABlankNameOrACompanyThatDoesNotExist()
    {
        CaseDto created = create("worth keeping", List.of(RCH));

        CaseUpdateDto blank = new CaseUpdateDto();
        blank.setName("  ");
        given().contentType(ContentType.JSON).body(blank)
                .when().put(PATH + "/" + created.getId())
                .then().statusCode(400)
                .body(containsString("cannot be blank"));

        CaseUpdateDto ghost = new CaseUpdateDto();
        ghost.setCompanyIds(List.of(999999L));
        given().contentType(ContentType.JSON).body(ghost)
                .when().put(PATH + "/" + created.getId())
                .then().statusCode(400);

        // Neither refusal leaves a mark on the case.
        CaseDto read = get(created.getId());
        assertThat(read.getName(), is("worth keeping"));
        assertThat(read.getCompanies().stream().map(CaseDto.Company::getTicker).toList(), contains("RCH"));

        delete(created.getId());
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

    @Test
    void readsTheTradesOpenedWhileTheCaseWasRunning()
    {
        // The seeded case has been running since 01.01.2023; company 1927 bought on 11.11.2023.
        List<CaseDto.Trade> bought = get(1L).getTrades().stream()
                .filter(trade -> trade.getPurchaseDate().toString().equals("2023-11-11"))
                .toList();

        assertThat(bought, hasSize(1));
        assertThat(bought.getFirst().getTicker(), is("NVDA"));
        assertThat(bought.getFirst().isActive(), is(false));
        assertThat(bought.getFirst().getCurrency(), is(notNullValue()));
    }

    @Test
    void readsTheTradesItsCompaniesOpenedWhileItRan()
    {
        // Company 1927 has a trade bought 11.11.2023 and sold 05.01.2024.
        CaseDto created = create("with trades", List.of(1927L));

        // The case opened today, so nothing bought years ago belongs to it.
        assertThat(created.getTrades(), is(empty()));

        CaseDto read = get(created.getId());
        assertThat(read.getTrades(), is(empty()));

        delete(created.getId());
    }

    @Test
    void readsTheTradesFromTheDayTheCaseWasOpened()
    {
        // Company 1927 has a trade bought 11.11.2023 and sold 05.01.2024.
        CaseDto created = create("opened around a trade", List.of(1927L));
        assertThat(created.getTrades(), is(empty()));

        CaseUpdateDto before = new CaseUpdateDto();
        before.setCreatedAt("2023-01-01");
        assertThat(update(created.getId(), before).getTrades().stream()
                        .map(trade -> trade.getPurchaseDate().toString()).toList(),
                contains("2023-11-11"));

        // Opened after it was bought, the trade is somebody else's business again.
        CaseUpdateDto after = new CaseUpdateDto();
        after.setCreatedAt("2024-01-01");
        assertThat(update(created.getId(), after).getTrades(), is(empty()));

        delete(created.getId());
    }

    @Test
    void deletesAClosedCaseWithEverythingWrittenIntoIt()
    {
        CaseDto created = create("written then deleted", List.of(RCH));
        // A day with a picture and a table, so the delete has something to take with it.
        CaseEntryUpdateDto entry = new CaseEntryUpdateDto();
        entry.setDate(LocalDate.now().toString());
        entry.setContent("[{\"type\":\"paragraph\",\"children\":[{\"text\":\"a day\"}]}]");
        Long entryId = given().contentType(ContentType.JSON).body(entry)
                .when().put("/case/entry/" + created.getId())
                .then().statusCode(200)
                .extract().response().jsonPath().getLong("id");
        given().when().post("/case/entry/" + entryId + "/table").then().statusCode(201);

        // An open case is not deleted by accident.
        given().when().delete(PATH + "/" + created.getId())
                .then().statusCode(400)
                .body(containsString("still open"));

        delete(created.getId());
        given().when().get(PATH + "/" + created.getId()).then().statusCode(400);
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

    /** A case is closed before it can be deleted, which is what the endpoint insists on. */
    private void delete(Long caseId)
    {
        CaseUpdateDto close = new CaseUpdateDto();
        close.setClosed(true);
        given().contentType(ContentType.JSON).body(close)
                .when().put(PATH + "/" + caseId).then().statusCode(200);
        given().when().delete(PATH + "/" + caseId).then().statusCode(200);
    }
}
