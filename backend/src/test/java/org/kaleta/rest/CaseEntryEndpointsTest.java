package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.rest.dto.CaseCreateDto;
import org.kaleta.rest.dto.CaseDto;
import org.kaleta.rest.dto.CaseEntryDto;
import org.kaleta.rest.dto.CaseEntryUpdateDto;
import org.kaleta.rest.dto.CaseTableDto;
import org.kaleta.rest.dto.CaseTableUpdateDto;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class CaseEntryEndpointsTest
{
    private static final String PATH = "/case/entry";
    private static final String TODAY = LocalDate.now().toString();

    @Test
    void writesTodayAndFillsTheSameDayInAgain()
    {
        Long caseId = createCase("written into");

        CaseEntryDto written = save(caseId, TODAY, document("first thoughts"));
        assertThat(written.date(), is(TODAY));
        assertThat(written.content(), is(document("first thoughts")));
        assertThat(written.images(), is(List.of()));

        CaseEntryDto again = save(caseId, TODAY, document("second thoughts"));
        assertThat(again.id(), is(written.id()));

        assertThat(readCase(caseId).getEntries(), hasSize(1));
        assertThat(readCase(caseId).getEntries().get(0).content(), is(document("second thoughts")));
    }

    @Test
    void writesNoDayOtherThanToday()
    {
        Long caseId = createCase("no back-dating");

        CaseEntryUpdateDto dto = new CaseEntryUpdateDto();
        dto.setDate(LocalDate.now().minusDays(1).toString());
        dto.setContent(document("yesterday"));

        given().contentType(ContentType.JSON).body(dto)
                .when().put(PATH + "/" + caseId)
                .then().statusCode(400)
                .body(containsString("written for today"));

        assertThat(readCase(caseId).getEntries(), hasSize(0));
    }

    @Test
    void keepsAnEmptiedDayOutOfTheWayWithoutDeletingIt()
    {
        Long caseId = createCase("emptied again");
        CaseEntryDto written = save(caseId, TODAY, document("something"));

        save(caseId, TODAY, "[{\"type\":\"paragraph\",\"children\":[{\"text\":\"\"}]}]");

        assertThat(readCase(caseId).getEntries(), hasSize(0));
        // The row is still there: writing that day again fills in the same entry.
        assertThat(save(caseId, TODAY, document("back again")).id(), is(written.id()));
    }

    @Test
    void addsAPictureToADayAndServesItBack() throws IOException
    {
        Long caseId = createCase("with a picture");
        CaseEntryDto written = save(caseId, TODAY, document("a chart below"));

        CaseEntryDto.Image image = given()
                .multiPart("file", "chart.png", chart(900, 400), "image/png")
                .when().post(PATH + "/" + written.id() + "/image")
                .then().log().ifError().statusCode(201)
                .extract().response().jsonPath().getObject("", CaseEntryDto.Image.class);

        assertThat(image.id(), is(image.id()));
        assertThat(readCase(caseId).getEntries().get(0).images(), hasSize(1));

        given().when().get(PATH + "/image/" + image.id())
                .then().statusCode(200).contentType("image/png");
        given().when().get(PATH + "/image/" + image.id() + "?thumbnail=true")
                .then().statusCode(200).contentType("image/png");

        // The text can be written again without taking the picture with it.
        save(caseId, TODAY, document("the text, rewritten"));
        assertThat(readCase(caseId).getEntries().get(0).images(), hasSize(1));

        given().when().delete(PATH + "/image/" + image.id()).then().statusCode(200);
        assertThat(readCase(caseId).getEntries().get(0).images(), hasSize(0));
    }

    @Test
    void refusesAPictureThatIsNotOne()
    {
        Long caseId = createCase("not a picture");
        CaseEntryDto written = save(caseId, TODAY, document("text only"));

        given().multiPart("file", "notes.txt", "not an image".getBytes(), "text/plain")
                .when().post(PATH + "/" + written.id() + "/image")
                .then().statusCode(400);
    }

    @Test
    void refusesADayForACaseThatDoesNotExist()
    {
        CaseEntryUpdateDto dto = new CaseEntryUpdateDto();
        dto.setDate(TODAY);
        dto.setContent(document("nowhere"));

        given().contentType(ContentType.JSON).body(dto)
                .when().put(PATH + "/999999")
                .then().statusCode(400);
    }

    @Test
    void writesATableIntoADayAndReadsItBack()
    {
        Long caseId = createCase("with a table");
        CaseEntryDto written = save(caseId, TODAY, document("the figures below"));

        CaseTableDto added = given()
                .when().post(PATH + "/" + written.id() + "/table")
                .then().log().ifError().statusCode(201)
                .extract().response().jsonPath().getObject("", CaseTableDto.class);
        assertThat(added.columns(), is(List.of()));

        CaseTableUpdateDto dto = new CaseTableUpdateDto();
        dto.setTitle("margins");
        dto.setColumns(List.of("NVDA", "AMD"));
        dto.setRows(List.of(row("gross", cell("75%", "++"), cell("53%", "~"))));

        CaseTableDto saved = given().contentType(ContentType.JSON).body(dto)
                .when().put(PATH + "/table/" + added.id())
                .then().log().ifError().statusCode(200)
                .extract().response().jsonPath().getObject("", CaseTableDto.class);
        assertThat(saved.title(), is("margins"));
        assertThat(saved.rows().get(0).cells().get(0).rating(), is("++"));

        CaseTableDto read = readCase(caseId).getEntries().get(0).tables().get(0);
        assertThat(read.columns(), is(List.of("NVDA", "AMD")));
        assertThat(read.rows().get(0).label(), is("gross"));
        assertThat(read.rows().get(0).cells().get(1).value(), is("53%"));

        given().when().delete(PATH + "/table/" + added.id()).then().statusCode(200);
        assertThat(readCase(caseId).getEntries().get(0).tables(), hasSize(0));
    }

    @Test
    void refusesARowThatDoesNotFitTheColumns()
    {
        Long caseId = createCase("ragged table");
        CaseEntryDto written = save(caseId, TODAY, document("text"));
        CaseTableDto added = given().when().post(PATH + "/" + written.id() + "/table")
                .then().statusCode(201)
                .extract().response().jsonPath().getObject("", CaseTableDto.class);

        CaseTableUpdateDto dto = new CaseTableUpdateDto();
        dto.setColumns(List.of("NVDA", "AMD"));
        dto.setRows(List.of(row("gross", cell("75%", "++"))));

        given().contentType(ContentType.JSON).body(dto)
                .when().put(PATH + "/table/" + added.id())
                .then().statusCode(400)
                .body(containsString("holds 1 cells"));
    }

    @Test
    void keepsADayThatHoldsOnlyATable()
    {
        Long caseId = createCase("table only");
        CaseEntryDto written = save(caseId, TODAY, document("to be emptied"));
        given().when().post(PATH + "/" + written.id() + "/table").then().statusCode(201);

        save(caseId, TODAY, "[{\"type\":\"paragraph\",\"children\":[{\"text\":\"\"}]}]");

        assertThat(readCase(caseId).getEntries(), hasSize(1));
    }

    private static CaseTableUpdateDto.Row row(String label, CaseTableUpdateDto.Cell... cells)
    {
        CaseTableUpdateDto.Row row = new CaseTableUpdateDto.Row();
        row.setLabel(label);
        row.setCells(List.of(cells));
        return row;
    }

    private static CaseTableUpdateDto.Cell cell(String value, String rating)
    {
        CaseTableUpdateDto.Cell cell = new CaseTableUpdateDto.Cell();
        cell.setValue(value);
        cell.setRating(rating);
        return cell;
    }

    @Test
    void movesADayToTheDateItWasReallyWrittenOn()
    {
        Long caseId = createCase("written down late");
        CaseEntryDto written = save(caseId, TODAY, document("this happened in March"));

        CaseEntryUpdateDto move = new CaseEntryUpdateDto();
        move.setDate("2026-03-14");
        CaseEntryDto moved = given().contentType(ContentType.JSON).body(move)
                .when().put(PATH + "/" + written.id() + "/date")
                .then().log().ifError().statusCode(200)
                .extract().response().jsonPath().getObject("", CaseEntryDto.class);

        assertThat(moved.id(), is(written.id()));
        assertThat(moved.date(), is("2026-03-14"));
        assertThat(readCase(caseId).getEntries().get(0).date(), is("2026-03-14"));

        // Today is free again, so it can be written into without colliding.
        assertThat(save(caseId, TODAY, document("and this today")).id(), is(not(written.id())));
    }

    @Test
    void refusesToMoveADayOntoOneTheCaseAlreadyHolds()
    {
        Long caseId = createCase("two days, one date");
        CaseEntryDto today = save(caseId, TODAY, document("today"));
        CaseEntryUpdateDto move = new CaseEntryUpdateDto();
        move.setDate("2026-02-02");
        given().contentType(ContentType.JSON).body(move)
                .when().put(PATH + "/" + today.id() + "/date").then().statusCode(200);

        CaseEntryDto second = save(caseId, TODAY, document("today again"));
        given().contentType(ContentType.JSON).body(move)
                .when().put(PATH + "/" + second.id() + "/date")
                .then().statusCode(400)
                .body(containsString("already holds an entry"));
    }

    private Long createCase(String name)
    {
        CaseCreateDto dto = new CaseCreateDto();
        dto.setName(name);
        return given().contentType(ContentType.JSON).body(dto)
                .when().post("/case")
                .then().statusCode(201)
                .extract().response().jsonPath().getObject("", CaseDto.class).getId();
    }

    private CaseDto readCase(Long caseId)
    {
        return given().when().get("/case/" + caseId)
                .then().statusCode(200)
                .extract().response().jsonPath().getObject("", CaseDto.class);
    }

    private CaseEntryDto save(Long caseId, String date, String content)
    {
        CaseEntryUpdateDto dto = new CaseEntryUpdateDto();
        dto.setDate(date);
        dto.setContent(content);
        return given().contentType(ContentType.JSON).body(dto)
                .when().put(PATH + "/" + caseId)
                .then().log().ifError().statusCode(200)
                .extract().response().jsonPath().getObject("", CaseEntryDto.class);
    }

    private static String document(String text)
    {
        return "[{\"type\":\"paragraph\",\"children\":[{\"text\":\"" + text + "\"}]}]";
    }

    private static byte[] chart(int width, int height) throws IOException
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLUE);
        graphics.drawLine(0, height, width, 0);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
