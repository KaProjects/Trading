package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.model.Trades;
import org.kaleta.rest.dto.TradeImportDto;
import org.kaleta.rest.dto.TradeImportPreviewDto;
import org.kaleta.rest.error.InvalidInputException;
import org.kaleta.service.FirebaseService;
import org.kaleta.service.TradeImportService;
import org.kaleta.service.TradeService;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class TradeImportEndpointsTest
{
    @InjectMock
    TradeImportService tradeImportService;
    @InjectMock
    TradeService tradeService;
    @InjectMock
    FirebaseService firebaseService;

    @BeforeEach
    void beforeEach()
    {
        clearInvocations(tradeImportService, tradeService, firebaseService);
        when(tradeService.getBy(true, null, null, null, null, null)).thenReturn(new Trades());
    }

    @Test
    void previewTradeImport()
    {
        TradeImportPreviewDto preview = new TradeImportPreviewDto();
        preview.setValid(true);
        when(tradeImportService.preview(anyString())).thenReturn(preview);

        given()
                .contentType("text/csv")
                .body("date,type,ticker,quantity,price,fees,portfolio")
                .when().post("/trade/import/preview")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("valid", is(true));
    }

    @Test
    void previewTradeImport_invalidCsv()
    {
        when(tradeImportService.preview(anyString())).thenThrow(new InvalidInputException("invalid CSV header"));

        given()
                .contentType("text/csv")
                .body("invalid")
                .when().post("/trade/import/preview")
                .then()
                .statusCode(400)
                .body(is("invalid CSV header"));
    }

    @Test
    void importTrades()
    {
        TradeImportDto dto = validImport();
        TradeImportPreviewDto preview = new TradeImportPreviewDto();
        preview.setValid(true);
        when(tradeImportService.importTrades(any())).thenReturn(preview);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/trade/import")
                .then()
                .statusCode(201)
                .body("valid", is(true));

        verify(firebaseService).pushAssets(any());
    }

    @Test
    void importTrades_invalidRows()
    {
        TradeImportPreviewDto preview = new TradeImportPreviewDto();
        preview.setValid(false);
        preview.setErrors(List.of(new TradeImportPreviewDto.Error(2, "quantity", "not enough shares")));
        when(tradeImportService.importTrades(any())).thenReturn(preview);

        given()
                .contentType(ContentType.JSON)
                .body(validImport())
                .when().post("/trade/import")
                .then()
                .statusCode(409)
                .body("valid", is(false))
                .body("errors[0].rowNumber", is(2))
                .body("errors[0].field", is("quantity"));

        verify(firebaseService, never()).pushAssets(any());
    }

    private TradeImportDto validImport()
    {
        TradeImportDto.Row row = new TradeImportDto.Row();
        row.setRowNumber(2);
        row.setDate("2026-01-10");
        row.setType("BUY");
        row.setTicker("NVDA");
        row.setQuantity("5");
        row.setPrice("145.5");
        row.setFees("4.95");
        row.setPortfolio("PATRIA_STANDARD");

        TradeImportDto dto = new TradeImportDto();
        dto.setRows(List.of(row));
        return dto;
    }
}
