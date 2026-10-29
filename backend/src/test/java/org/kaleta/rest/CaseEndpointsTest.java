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
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.not;
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
        assertThat(tickers(created), contains("RCH", "UPD"));

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
        dto.setCompanies(List.of(company(UPD, null)));
        CaseDto updated = update(created.getId(), dto);

        assertThat(updated.getName(), is("the better name"));
        assertThat(tickers(updated), contains("UPD"));

        // Read again rather than trusting the answer: a column the mapping refuses to write
        // still answers with what was asked for.
        CaseDto read = get(created.getId());
        assertThat(read.getName(), is("the better name"));
        assertThat(tickers(read), contains("UPD"));

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

        // One list, the companies the app holds and the names written for the ones it does not.
        assertThat(tickers(created), contains("Broadcom", "RCH", "TSMC"));
        assertThat(created.getCompanies().stream()
                .filter(company -> company.getId() == null)
                .map(CaseDto.Company::getTicker).toList(), contains("Broadcom", "TSMC"));

        CaseUpdateDto update = new CaseUpdateDto();
        update.setCompanies(List.of(company(RCH, null), written("Samsung", null)));
        CaseDto updated = update(created.getId(), update);
        assertThat(tickers(updated), contains("RCH", "Samsung"));
        assertThat(tickers(get(created.getId())), contains("RCH", "Samsung"));

        CaseUpdateDto tooLong = new CaseUpdateDto();
        tooLong.setCompanies(List.of(written("x".repeat(51), null)));
        given().contentType(ContentType.JSON).body(tooLong)
                .when().put(PATH + "/" + created.getId())
                .then().statusCode(400);

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
    void readsWhatAnOpenPositionLastTradedAt()
    {
        // RCH has two trades still open, and a price the app last saw it at.
        CaseDto created = create("still holding", List.of(RCH));
        CaseUpdateDto opened = new CaseUpdateDto();
        opened.setCompanies(List.of(company(RCH, "2020-01-01")));
        CaseDto read = update(created.getId(), opened);

        List<CaseDto.Trade> open = read.getTrades().stream().filter(CaseDto.Trade::isActive).toList();
        assertThat(open, is(not(empty())));
        assertThat(open.getFirst().getCurrentPrice(), is(notNullValue()));

        // A trade that is over says nothing about today's price; what it made is settled.
        read.getTrades().stream().filter(trade -> !trade.isActive())
                .forEach(trade -> assertThat(trade.getCurrentPrice(), is(nullValue())));

        delete(created.getId());
    }

    @Test
    void readsWhatTheAppHoldsAboutItsCompaniesWhileItCoversThem()
    {
        CaseDto created = create("what else happened", List.of(RCH));

        // Nothing is mentioned before the company joined, which it did today.
        assertThat(created.getMentions(), is(empty()));

        CaseUpdateDto earlier = new CaseUpdateDto();
        earlier.setCompanies(List.of(company(RCH, "2000-01-01")));
        CaseDto read = update(created.getId(), earlier);

        assertThat(read.getMentions(), is(not(empty())));
        read.getMentions().forEach(mention -> {
            assertThat(mention.getKind(), isIn(List.of("RECORD", "RESEARCH", "REPORT")));
            assertThat(mention.getTicker(), is("RCH"));
            assertThat(mention.getId(), is(notNullValue()));
            assertThat(mention.getDate(), is(notNullValue()));
        });

        // Oldest first, as the stream reads them.
        List<String> dates = read.getMentions().stream()
                .map(mention -> mention.getDate().toString()).toList();
        assertThat(dates, is(dates.stream().sorted().toList()));

        delete(created.getId());
    }

    @Test
    void keepsTheDayEachCompanyJoinedTheCase()
    {
        CaseDto created = create("grown over time", List.of(RCH));
        assertThat(created.getCompanies().get(0).getAddedAt().toLocalDate(), is(LocalDate.now()));

        // A second company joins, and is dated to the day it was really taken in.
        CaseUpdateDto added = new CaseUpdateDto();
        added.setCompanies(List.of(company(RCH, null), company(UPD, "2026-03-04")));
        CaseDto updated = update(created.getId(), added);

        // Oldest in the case first, whatever the tickers are.
        assertThat(tickers(updated), contains("UPD", "RCH"));
        assertThat(updated.getCompanies().get(0).getAddedAt().toLocalDate(),
                is(LocalDate.of(2026, 3, 4)));
        // The one that was already there keeps the day it joined.
        assertThat(updated.getCompanies().get(1).getAddedAt().toLocalDate(), is(LocalDate.now()));

        // Left out of the list is off the case.
        CaseUpdateDto removed = new CaseUpdateDto();
        removed.setCompanies(List.of(company(UPD, null)));
        CaseDto after = update(created.getId(), removed);
        assertThat(tickers(after), contains("UPD"));
        assertThat(after.getCompanies().get(0).getAddedAt().toLocalDate(),
                is(LocalDate.of(2026, 3, 4)));

        delete(created.getId());
    }

    @Test
    void readsTheTradesOfACompanyFromTheDayItJoinedTheCase()
    {
        // Company 1927 has a trade bought 11.11.2023 and sold 05.01.2024.
        CaseDto created = create("joined late", List.of(1927L));

        CaseUpdateDto opened = new CaseUpdateDto();
        opened.setCreatedAt("2023-01-01");
        // The case has been running since 2023, but the company only joined in 2024.
        opened.setCompanies(List.of(company(1927L, "2024-01-01")));
        assertThat(update(created.getId(), opened).getTrades(), is(empty()));

        // Dated back to before the trade, the case answers for it.
        CaseUpdateDto earlier = new CaseUpdateDto();
        earlier.setCompanies(List.of(company(1927L, "2023-01-01")));
        assertThat(update(created.getId(), earlier).getTrades().stream()
                        .map(trade -> trade.getPurchaseDate().toString()).toList(),
                contains("2023-11-11"));

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
        ghost.setCompanies(List.of(company(999999L, null)));
        given().contentType(ContentType.JSON).body(ghost)
                .when().put(PATH + "/" + created.getId())
                .then().statusCode(400);

        // Neither refusal leaves a mark on the case.
        CaseDto read = get(created.getId());
        assertThat(read.getName(), is("worth keeping"));
        assertThat(tickers(read), contains("RCH"));

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

    private static List<String> tickers(CaseDto dto)
    {
        return dto.getCompanies().stream().map(CaseDto.Company::getTicker).toList();
    }

    private static CaseUpdateDto.Company company(Long companyId, String addedAt)
    {
        CaseUpdateDto.Company company = new CaseUpdateDto.Company();
        company.setId(companyId);
        company.setAddedAt(addedAt);
        return company;
    }

    private static CaseUpdateDto.Company written(String name, String addedAt)
    {
        CaseUpdateDto.Company company = new CaseUpdateDto.Company();
        company.setName(name);
        company.setAddedAt(addedAt);
        return company;
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
