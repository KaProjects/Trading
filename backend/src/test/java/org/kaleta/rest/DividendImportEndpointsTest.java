package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.rest.dto.DividendImportDto;
import org.kaleta.rest.dto.DividendImportPreviewDto;
import org.kaleta.rest.error.InvalidInputException;
import org.kaleta.service.DividendImportService;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;

@QuarkusTest
class DividendImportEndpointsTest
{
    @InjectMock
    DividendImportService dividendImportService;

    @BeforeEach
    void beforeEach()
    {
        clearInvocations(dividendImportService);
    }

    @Test
    void previewDividendImport()
    {
        DividendImportPreviewDto preview = new DividendImportPreviewDto();
        preview.setValid(true);
        when(dividendImportService.preview(anyString())).thenReturn(preview);

        given()
                .contentType("text/csv")
                .body("date,ticker,dividend,tax")
                .when().post("/dividend/import/preview")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("valid", is(true));
    }

    @Test
    void previewDividendImport_invalidCsv()
    {
        when(dividendImportService.preview(anyString()))
                .thenThrow(new InvalidInputException("invalid CSV header"));

        given()
                .contentType("text/csv")
                .body("invalid")
                .when().post("/dividend/import/preview")
                .then()
                .statusCode(400)
                .body(is("invalid CSV header"));
    }

    @Test
    void importDividends()
    {
        DividendImportPreviewDto preview = new DividendImportPreviewDto();
        preview.setValid(true);
        when(dividendImportService.importDividends(any())).thenReturn(preview);

        given()
                .contentType(ContentType.JSON)
                .body(validImport())
                .when().post("/dividend/import")
                .then()
                .statusCode(201)
                .body("valid", is(true));
    }

    @Test
    void importDividends_invalidRows()
    {
        DividendImportPreviewDto preview = new DividendImportPreviewDto();
        preview.setValid(false);
        preview.setErrors(List.of(new DividendImportPreviewDto.Error(
                2, "ticker", "company 'BAD' was not found")));
        when(dividendImportService.importDividends(any())).thenReturn(preview);

        given()
                .contentType(ContentType.JSON)
                .body(validImport())
                .when().post("/dividend/import")
                .then()
                .statusCode(409)
                .body("valid", is(false))
                .body("errors[0].rowNumber", is(2))
                .body("errors[0].field", is("ticker"));
    }

    private DividendImportDto validImport()
    {
        DividendImportDto.Row row = new DividendImportDto.Row();
        row.setRowNumber(2);
        row.setDate("2026-01-10");
        row.setTicker("NVDA");
        row.setDividend("20.50");
        row.setTax("3.25");

        DividendImportDto dto = new DividendImportDto();
        dto.setRows(List.of(row));
        return dto;
    }
}
