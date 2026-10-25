package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.persistence.api.PeriodContentDao;
import org.kaleta.rest.dto.PeriodContentDto;
import org.kaleta.rest.dto.PeriodContentUpdateDto;
import org.kaleta.rest.dto.ResearchDto;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;

@QuarkusTest
class PeriodContentEndpointsTest
{
    private static final String PATH = "/period/content";
    // PCNT 25Q1, reported 2025-05-01: its window runs 01.02.2025 - 01.05.2025.
    private static final Long PERIOD_ID = 1468L;
    private static final Long COMPANY_ID = 1567L;

    @Inject
    PeriodContentDao periodContentDao;

    @Test
    void writesAnEntryAndFillsItInAgainOnTheSameDate()
    {
        PeriodContentDto created = save(PERIOD_ID, "2025-03-10", document("first thoughts"));
        assertThat(created.date(), is("2025-03-10"));
        assertThat(created.content(), is(document("first thoughts")));
        assertThat(created.images(), is(List.of()));

        // The date is the entry's identity: writing it again fills in the same row.
        PeriodContentDto rewritten = save(PERIOD_ID, "2025-03-10", document("second thoughts"));
        assertThat(rewritten.id(), is(created.id()));
        assertThat(rewritten.content(), is(document("second thoughts")));

        List<ResearchDto.PeriodDto> periods = research();
        ResearchDto.PeriodDto period = periods.stream()
                .filter(candidate -> candidate.getId().equals(PERIOD_ID))
                .findFirst().orElseThrow();
        assertThat(period.getContents(), hasSize(1));
        assertThat(period.getContents().get(0).content(), is(document("second thoughts")));

        emptied(created.id());
    }

    @Test
    void keepsAnEmptiedEntryOutOfTheWayWithoutDeletingIt()
    {
        PeriodContentDto created = save(PERIOD_ID, "2025-03-11", document("something"));
        save(PERIOD_ID, "2025-03-11", "[{\"type\":\"paragraph\",\"children\":[{\"text\":\"\"}]}]");

        // Gone from the page, still in the database, and the same row is written into next time.
        assertThat(contentsOf(PERIOD_ID), is(List.of()));
        assertThat(periodContentDao.findByDate(PERIOD_ID, java.sql.Date.valueOf("2025-03-11"))
                .orElseThrow().getId(), is(created.id()));
        assertThat(save(PERIOD_ID, "2025-03-11", document("back again")).id(), is(created.id()));

        emptied(created.id());
    }

    @Test
    void keepsAnEntryInsideThePeriodItWasWrittenFor()
    {
        // Both report dates belong to the period.
        PeriodContentDto start = save(PERIOD_ID, "2025-02-01", document("on the previous report"));
        PeriodContentDto end = save(PERIOD_ID, "2025-05-01", document("on the report day"));

        given().contentType(ContentType.JSON)
                .body(updateDto("2025-05-02", document("a day too late")))
                .when().put(PATH + "/" + PERIOD_ID)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString("must be between '2025-02-01' and '2025-05-01'"));

        emptied(start.id());
        emptied(end.id());
    }

    @Test
    void takesAnyDateWhereThePeriodHasNoWindow()
    {
        // WNOW 25Q3 has no report date and nothing reported before it, so there is nothing to
        // hold a date to; the page marks such an entry instead of the API refusing it.
        PeriodContentDto created = save(1470L, "2021-01-01", document("whenever"));
        assertThat(created.date(), is("2021-01-01"));

        emptied(created.id());
    }

    @Test
    void addsAnImageToAnEntryAndServesItBack() throws IOException
    {
        PeriodContentDto entry = save(PERIOD_ID, "2025-03-12", document("with a chart"));

        PeriodContentDto.Image image = given()
                .multiPart("file", "chart.png", chart(2400, 1200), "image/png")
                .when().post(PATH + "/" + entry.id() + "/image")
                .then().log().ifError()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract().as(PeriodContentDto.Image.class);

        assertThat(image.width(), is(1600));
        assertThat(image.height(), is(800));
        assertThat(contentsOf(PERIOD_ID).get(0).images(), hasSize(1));

        byte[] served = given().when().get(PATH + "/image/" + image.id())
                .then().statusCode(Response.Status.OK.getStatusCode())
                .contentType("image/png")
                .header("Cache-Control", containsString("max-age"))
                .extract().asByteArray();
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(served)).getWidth(), is(1600));

        byte[] thumbnail = given().when().get(PATH + "/image/" + image.id() + "?thumbnail=true")
                .then().statusCode(Response.Status.OK.getStatusCode())
                .extract().asByteArray();
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(thumbnail)).getWidth(), is(320));

        given().when().delete(PATH + "/image/" + image.id())
                .then().statusCode(Response.Status.OK.getStatusCode());
        // The last image gone and no text left, the entry drops out of the page again.
        assertThat(contentsOf(PERIOD_ID).get(0).images(), is(List.of()));

        emptied(entry.id());
    }

    @Test
    void keepsThePicturesWhenTheTextIsWrittenAgain() throws IOException
    {
        PeriodContentDto entry = save(PERIOD_ID, "2025-03-14", document("first draft"));
        given().multiPart("file", "chart.png", chart(800, 400), "image/png")
                .when().post(PATH + "/" + entry.id() + "/image")
                .then().statusCode(Response.Status.CREATED.getStatusCode());

        // Writing the text again used to take the day's pictures with it.
        PeriodContentDto rewritten = save(PERIOD_ID, "2025-03-14", document("second draft"));

        assertThat(rewritten.content(), is(document("second draft")));
        assertThat(rewritten.images(), hasSize(1));
        assertThat(contentsOf(PERIOD_ID).get(0).images(), hasSize(1));

        emptied(entry.id());
    }

    @Test
    void handsBackTheNewestPictureFirst() throws IOException
    {
        PeriodContentDto entry = save(PERIOD_ID, "2025-03-15", document("three charts"));
        List<Long> uploaded = List.of(
                upload(entry.id(), 600, 300),
                upload(entry.id(), 620, 300),
                upload(entry.id(), 640, 300));

        List<Long> shown = contentsOf(PERIOD_ID).get(0).images().stream()
                .map(PeriodContentDto.Image::id)
                .toList();

        assertThat(shown, is(List.of(uploaded.get(2), uploaded.get(1), uploaded.get(0))));

        emptied(entry.id());
    }

    @Test
    void saysWhyAnUploadWasNotTakenIn() throws IOException
    {
        PeriodContentDto entry = save(PERIOD_ID, "2025-03-13", document("with a bad upload"));

        given().multiPart("file", "notes.txt", "not an image".getBytes(), "text/plain")
                .when().post(PATH + "/" + entry.id() + "/image")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString("not a PNG or a JPEG"));

        given().multiPart("other", "ignored.png", new byte[]{1, 2, 3}, "image/png")
                .when().post(PATH + "/" + entry.id() + "/image")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString("no file was uploaded"));

        emptied(entry.id());
    }

    @Test
    void invalidParameters()
    {
        given().accept(ContentType.JSON).when().get(PATH + "/image/0")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString(VALID_ID));
        given().accept(ContentType.JSON).when().get(PATH + "/image/4294967295")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString("image with id '4294967295' not found"));

        given().contentType(ContentType.JSON)
                .body(updateDto("10.03.2025", document("wrong format")))
                .when().put(PATH + "/" + PERIOD_ID)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString("must match YYYY-MM-DD"));

        given().contentType(ContentType.JSON)
                .body(updateDto("2025-03-10", document("no such period")))
                .when().put(PATH + "/4294967295")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString("period with id '4294967295' not found"));
    }

    private Long upload(Long contentId, int width, int height) throws IOException
    {
        return given().multiPart("file", "chart.png", chart(width, height), "image/png")
                .when().post(PATH + "/" + contentId + "/image")
                .then().log().ifError()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract().as(PeriodContentDto.Image.class)
                .id();
    }

    private PeriodContentDto save(Long periodId, String date, String content)
    {
        return given().contentType(ContentType.JSON)
                .body(updateDto(date, content))
                .when().put(PATH + "/" + periodId)
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().as(PeriodContentDto.class);
    }

    private PeriodContentUpdateDto updateDto(String date, String content)
    {
        PeriodContentUpdateDto dto = new PeriodContentUpdateDto();
        dto.setDate(date);
        dto.setContent(content);
        return dto;
    }

    private List<ResearchDto.PeriodDto> research()
    {
        return given().when().get("/research/" + COMPANY_ID)
                .then().statusCode(Response.Status.OK.getStatusCode())
                .extract().as(ResearchDto.class)
                .getPeriods();
    }

    private List<PeriodContentDto> contentsOf(Long periodId)
    {
        return research().stream()
                .filter(period -> period.getId().equals(periodId))
                .findFirst().orElseThrow()
                .getContents();
    }

    /** Leaves the row behind as the app does, so the next test starts from an empty page. */
    private void emptied(Long contentId)
    {
        periodContentDao.listImageSizes(contentId).stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList()
                .forEach(periodContentDao::deleteImage);
        periodContentDao.updateContent(contentId, null);
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
