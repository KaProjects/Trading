package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.rest.dto.DividendCreateDto;
import org.kaleta.rest.dto.DividendImportDto;
import org.kaleta.rest.dto.DividendImportPreviewDto;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class DividendImportServiceTest
{
    @InjectMock
    CompanyDao companyDao;
    @InjectMock
    DividendService dividendService;
    @Inject
    DividendImportService dividendImportService;

    private Company company;

    @BeforeEach
    void beforeEach()
    {
        company = new Company();
        company.setId(100L);
        company.setTicker("NVDA");
        company.setCurrency(Currency.$);
        when(companyDao.list()).thenReturn(List.of(company));
    }

    @Test
    void previewSortsRowsAndCalculatesNetAmount()
    {
        String csv = "date,ticker,dividend,tax\n"
                + "2026-03-15,NVDA,20.50,3.25\n"
                + "2026-01-10,nvda,10,1.50";

        DividendImportPreviewDto preview = dividendImportService.preview(csv);

        assertThat(preview.isValid(), is(true));
        assertThat(preview.isReordered(), is(true));
        assertThat(preview.getRows().stream().map(DividendImportPreviewDto.Row::getRowNumber).toList(),
                contains(3, 2));
        assertThat(preview.getRows().getFirst().getTicker(), is("NVDA"));
        assertThat(preview.getRows().getFirst().getNet(), is("8.5"));
        assertThat(preview.getRows().getLast().getNet(), is("17.25"));
    }

    @Test
    void previewReturnsAllRowErrorsWithoutWriting()
    {
        String csv = "date,ticker,dividend,tax\n"
                + "2026-02-30,MISSING,-1,1.234";

        DividendImportPreviewDto preview = dividendImportService.preview(csv);

        assertThat(preview.isValid(), is(false));
        assertThat(preview.getErrors().stream().map(DividendImportPreviewDto.Error::getField).toList(),
                containsInAnyOrder("date", "ticker", "dividend", "tax"));
        verify(dividendService, times(0)).createDividend(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void previewRejectsMalformedHeader()
    {
        InvalidInputException exception = assertThrows(InvalidInputException.class,
                () -> dividendImportService.preview("date,ticker,amount\n2026-01-01,NVDA,10"));

        assertThat(exception.getMessage(), is("CSV header must be: date,ticker,dividend,tax"));
    }

    @Test
    void importCreatesEveryDividendInChronologicalOrder()
    {
        DividendImportDto dto = new DividendImportDto();
        dto.setRows(List.of(
                row(2, "2026-03-15", "20.50", "3.25"),
                row(3, "2026-01-10", "10", "1.50")));

        DividendImportPreviewDto result = dividendImportService.importDividends(dto);

        assertThat(result.isValid(), is(true));
        ArgumentCaptor<DividendCreateDto> captor = ArgumentCaptor.forClass(DividendCreateDto.class);
        verify(dividendService, times(2)).createDividend(captor.capture());
        assertThat(captor.getAllValues().stream().map(DividendCreateDto::getDate).toList(),
                contains("2026-01-10", "2026-03-15"));
        assertThat(captor.getAllValues().getFirst().getCompanyId(), is(100L));
        assertThat(captor.getAllValues().getFirst().getDividend(), is("10"));
        assertThat(captor.getAllValues().getFirst().getTax(), is("1.50"));
    }

    private DividendImportDto.Row row(int rowNumber, String date, String dividend, String tax)
    {
        DividendImportDto.Row row = new DividendImportDto.Row();
        row.setRowNumber(rowNumber);
        row.setDate(date);
        row.setTicker("NVDA");
        row.setDividend(dividend);
        row.setTax(tax);
        return row;
    }
}
