package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.model.TradeSaleSummary;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.TradeImportDto;
import org.kaleta.rest.dto.TradeImportPreviewDto;
import org.kaleta.rest.dto.TradeSellDto;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class TradeImportServiceTest
{
    @InjectMock
    CompanyDao companyDao;
    @InjectMock
    TradeDao tradeDao;
    @InjectMock
    TradeService tradeService;
    @InjectMock
    RecordService recordService;
    @Inject
    TradeImportService tradeImportService;

    private Company company;

    @BeforeEach
    void beforeEach()
    {
        company = new Company();
        company.setId(100L);
        company.setTicker("NVDA");
        company.setCurrency(Currency.$);
        when(companyDao.list()).thenReturn(List.of(company));
        when(tradeDao.list(true, null, null, null, null, null)).thenReturn(List.of());
    }

    @Test
    void previewSortsRowsAndAllocatesExistingAndCsvLotsUsingFifo()
    {
        Trade first = activeTrade(10L, "2026-01-01", "2");
        Trade third = activeTrade(11L, "2026-02-01", "4");
        when(tradeDao.list(true, null, null, null, null, null)).thenReturn(List.of(third, first));

        String csv = "date,type,ticker,quantity,price,fees,portfolio\n"
                + "2026-03-01,SELL,NVDA,8,180,6.95,PATRIA_STANDARD\n"
                + "2026-01-10,BUY,NVDA,3,145.50,4.95,PATRIA_STANDARD";

        TradeImportPreviewDto preview = tradeImportService.preview(csv);

        assertThat(preview.isValid(), is(true));
        assertThat(preview.isReordered(), is(true));
        assertThat(preview.getRows().stream().map(TradeImportPreviewDto.Row::getRowNumber).toList(), contains(3, 2));

        TradeImportPreviewDto.Row sale = preview.getRows().get(1);
        assertThat(sale.getAllocations().stream().map(TradeImportPreviewDto.Allocation::getSource).toList(),
                contains("Existing trade #10", "CSV row 3", "Existing trade #11"));
        assertThat(sale.getAllocations().stream().map(TradeImportPreviewDto.Allocation::getQuantity).toList(),
                contains("2", "3", "3"));
        assertThat(sale.getRemainingQuantity(), is("1"));
    }

    @Test
    void previewReturnsAllFieldAndAvailabilityErrorsWithoutWriting()
    {
        when(tradeDao.list(true, null, null, null, null, null))
                .thenReturn(List.of(activeTrade(10L, "2026-01-01", "2")));
        String csv = "date,type,ticker,quantity,price,fees,portfolio\n"
                + "2026-02-30,HOLD,MISSING,-1,1234567,1.234,\n"
                + "2026-03-01,SELL,NVDA,3,180,6.95,PATRIA_STANDARD";

        TradeImportPreviewDto preview = tradeImportService.preview(csv);

        assertThat(preview.isValid(), is(false));
        assertThat(preview.getErrors().stream().map(TradeImportPreviewDto.Error::getField).toList(),
                containsInAnyOrder("date", "type", "ticker", "quantity", "price", "fees", "portfolio", "quantity"));
        assertThat(preview.getErrors().getLast().getMessage(),
                is("cannot sell 3; only 2 is available for NVDA in PATRIA_STANDARD on 2026-03-01"));
    }

    @Test
    void previewRejectsMalformedHeader()
    {
        InvalidInputException exception = assertThrows(InvalidInputException.class,
                () -> tradeImportService.preview("date,type,ticker\n2026-01-01,BUY,NVDA"));

        assertThat(exception.getMessage(),
                is("CSV header must be: date,type,ticker,quantity,price,fees,portfolio"));
    }

    @Test
    void importCreatesBuyAndItsRecord()
    {
        TradeImportDto dto = importDto(row(2, "2026-01-10", "BUY", "3", "145.5", "4.95"));

        TradeImportPreviewDto result = tradeImportService.importTrades(dto);

        assertThat(result.isValid(), is(true));
        ArgumentCaptor<org.kaleta.rest.dto.TradeCreateDto> createCaptor =
                ArgumentCaptor.forClass(org.kaleta.rest.dto.TradeCreateDto.class);
        verify(tradeService).createTrade(createCaptor.capture());
        assertThat(createCaptor.getValue().getCompanyId(), is(100L));
        assertThat(createCaptor.getValue().getPortfolio(), is(Portfolio.PATRIA_STANDARD.name()));
        verify(recordService).createCurrent(100L, "bought 3", "2026-01-10", "145.5");
    }

    @Test
    void importBuildsFifoSaleAndCreatesOneAggregateRecord()
    {
        Trade trade = activeTrade(10L, "2026-01-01", "5");
        when(tradeDao.list(true, null, null, null, null, null)).thenReturn(List.of(trade));
        when(tradeDao.list(true, 100L, null, null, null, null, Portfolio.PATRIA_STANDARD.name()))
                .thenReturn(List.of(trade));
        TradeSaleSummary summary = new TradeSaleSummary(
                new BigDecimal("3"),
                new BigDecimal("100"),
                new BigDecimal("5"),
                new BigDecimal("40"),
                new BigDecimal("12.5"));
        when(tradeService.sellTrade(any())).thenReturn(summary);
        TradeImportDto dto = importDto(row(2, "2026-03-01", "SELL", "3", "120", "2"));

        TradeImportPreviewDto result = tradeImportService.importTrades(dto);

        assertThat(result.isValid(), is(true));
        ArgumentCaptor<TradeSellDto> sellCaptor = ArgumentCaptor.forClass(TradeSellDto.class);
        verify(tradeService).sellTrade(sellCaptor.capture());
        assertThat(sellCaptor.getValue().getTrades().size(), is(1));
        assertThat(sellCaptor.getValue().getTrades().getFirst().getTradeId(), is(10L));
        assertThat(sellCaptor.getValue().getTrades().getFirst().getQuantity(), is("3"));
        verify(recordService).createCurrent(100L, "sold 3", "2026-03-01", "120", summary);
    }

    private Trade activeTrade(Long id, String date, String quantity)
    {
        Trade trade = new Trade();
        trade.setId(id);
        trade.setCompany(company);
        trade.setPurchaseDate(Date.valueOf(date));
        trade.setQuantity(new BigDecimal(quantity));
        trade.setPurchasePrice(new BigDecimal("100"));
        trade.setPurchaseFees(new BigDecimal("5"));
        trade.setPortfolio(Portfolio.PATRIA_STANDARD);
        return trade;
    }

    private TradeImportDto importDto(TradeImportDto.Row row)
    {
        TradeImportDto dto = new TradeImportDto();
        dto.setRows(List.of(row));
        return dto;
    }

    private TradeImportDto.Row row(int rowNumber, String date, String type, String quantity,
                                   String price, String fees)
    {
        TradeImportDto.Row row = new TradeImportDto.Row();
        row.setRowNumber(rowNumber);
        row.setDate(date);
        row.setType(type);
        row.setTicker("NVDA");
        row.setQuantity(quantity);
        row.setPrice(price);
        row.setFees(fees);
        row.setPortfolio(Portfolio.PATRIA_STANDARD.name());
        return row;
    }
}
