package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.client.AlphaVantageClient;
import org.kaleta.client.PolygonClient;
import org.kaleta.client.dto.AlphaVantageEarnings;
import org.kaleta.client.dto.AlphaVantagePriceRange;
import org.kaleta.client.dto.AlphaVantageShares;
import org.kaleta.framework.Generator;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.EstimateImportDto;
import org.kaleta.rest.dto.PeriodImportDataDto;
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.error.InvalidInputException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class ImportServiceTest
{
    @InjectMock
    CompanyService companyService;
    @InjectMock
    PeriodService periodService;
    @InjectMock
    @MockitoConfig(convertScopes = true)
    FirebaseService firebaseService;
    @InjectMock
    AlphaVantageClient alphaVantageClient;
    @InjectMock
    PolygonClient polygonClient;

    @Inject
    ImportService importService;

    private Company company;
    private Period period;

    @BeforeEach
    void before()
    {
        reset(companyService, periodService, firebaseService, alphaVantageClient, polygonClient);
        company = Generator.generateCompany(100L);
        company.setTicker("NVDA");
        company.setCurrency(Currency.$);
        period = Generator.generatePeriod(
                company,
                false,
                PeriodName.valueOf("26Q1"),
                YearMonth.of(2026, 4));
        period.setId(200L);
        when(companyService.findEntity(company.getId())).thenReturn(company);
        when(periodService.get(period.getId())).thenReturn(period);
    }

    @Test
    void getEstimate()
    {
        when(firebaseService.getReportingCurrency("NVDA")).thenReturn("$");
        EstimateImportDto.Quarter current = quarter("5", "2026-05-01");
        EstimateImportDto.Quarter next1 = quarter("6", "2026-08-01");
        EstimateImportDto.Quarter next3 = quarter("8", "2027-02-01");
        when(firebaseService.getLatestEstimate("NVDA", "26Q1")).thenReturn(current);
        when(firebaseService.getLatestEstimate("NVDA", "26Q2")).thenReturn(next1);
        when(firebaseService.getLatestEstimate("NVDA", "26Q4")).thenReturn(next3);

        EstimateImportDto result = importService.getEstimate(company.getId(), period.getId());

        assertThat(result.getCurrent(), is(current));
        assertThat(result.getNext1(), is(next1));
        assertThat(result.getNext2(), is(nullValue()));
        assertThat(result.getNext3(), is(next3));
        verify(firebaseService).getLatestEstimate("NVDA", "26Q1");
        verify(firebaseService).getLatestEstimate("NVDA", "26Q2");
        verify(firebaseService).getLatestEstimate("NVDA", "26Q3");
        verify(firebaseService).getLatestEstimate("NVDA", "26Q4");
    }

    @Test
    void getEstimate_withoutGeminiCurrencyUsesFinnhubSuggestions()
    {
        EstimateImportDto.Quarter current = quarter("5", "2026-05-01");
        when(firebaseService.getLatestEstimate("NVDA", "26Q1")).thenReturn(current);

        EstimateImportDto result = importService.getEstimate(company.getId(), period.getId());

        assertThat(result.getCurrent(), is(current));
        assertThat(result.getWarnings().isEmpty(), is(true));
    }

    @Test
    void getEstimate_withMismatchedGeminiCurrencyRejectsFinnhubSuggestions()
    {
        when(firebaseService.getReportingCurrency("NVDA")).thenReturn("€");

        EstimateImportDto result = importService.getEstimate(company.getId(), period.getId());

        assertThat(result.getCurrent(), is(nullValue()));
        assertThat(result.getWarnings(), is(java.util.List.of(
                "Firebase/Gemini financial data for 26Q1 and NVDA was ignored because reported currency € "
                        + "does not match the company's configured currency $")));
    }

    @Test
    void getEstimate_rejectsPeriodFromAnotherCompany()
    {
        period.setCompany(Generator.generateCompany(101L));

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> importService.getEstimate(company.getId(), period.getId()));

        assertThat(exception.getMessage(), is(
                "period with id '200' does not belong to company with id '100'"));
    }

    @Test
    void getEstimate_rejectsNonQuarterPeriod()
    {
        period.setName(PeriodName.valueOf("26FY"));

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> importService.getEstimate(company.getId(), period.getId()));

        assertThat(exception.getMessage(), is("period with id '200' is not a quarter"));
    }

    @Test
    void getPeriod_nonUsdUsesConfiguredAlphaVantageTickerForAllSuggestions() throws Exception
    {
        org.kaleta.model.Company model = new org.kaleta.model.Company();
        model.setId(company.getId());
        model.setTicker("ASML");
        model.setAlphaVantageTicker("ASML.AMS");
        model.setCurrency(Currency.€);
        when(companyService.getCompany(company.getId())).thenReturn(model);

        PeriodImportDto firebaseData = new PeriodImportDto();
        firebaseData.setName("26Q2");
        firebaseData.setEndingMonth("2026-06");
        firebaseData.setPreviousReportDate("2026-04-22");
        firebaseData.setReportDate("2026-07-22");
        when(firebaseService.getPeriod("ASML", "26Q2")).thenReturn(firebaseData);
        when(alphaVantageClient.getShares("ASML.AMS", "26Q2", "2026-06"))
                .thenReturn(Optional.of(new AlphaVantageShares(
                        new BigDecimal("393000000"), "EUR")));
        when(alphaVantageClient.getEarnings("ASML.AMS", "26Q2", "2026-06"))
                .thenReturn(Optional.of(new AlphaVantageEarnings(new BigDecimal("7.42"))));
        when(alphaVantageClient.getPriceRange(
                "ASML.AMS", "2026-04-22", "2026-07-22"))
                .thenReturn(Optional.of(new AlphaVantagePriceRange(
                        new BigDecimal("1550.40"), new BigDecimal("1178.20"))));

        PeriodImportDataDto result = importService.getPeriod(company.getId(), "26Q2", null);

        assertThat(result.getAlphaVantage().getShares(), is("393"));
        assertThat(result.getAlphaVantage().getAdjustedEps(), is("7.42"));
        assertThat(result.getAlphaVantage().getPriceHigh(), is("1550.4"));
        assertThat(result.getAlphaVantage().getPriceLow(), is("1178.2"));
        assertThat(result.getWarnings(), is(java.util.List.of()));
        verify(alphaVantageClient).getCashFlow("ASML.AMS", "26Q2", "2026-06");
        verify(alphaVantageClient).getIncomeStatement("ASML.AMS", "26Q2", "2026-06");
        verify(alphaVantageClient).getShares("ASML.AMS", "26Q2", "2026-06");
        verify(alphaVantageClient).getEarnings("ASML.AMS", "26Q2", "2026-06");
        verify(alphaVantageClient).getPriceRange("ASML.AMS", "2026-04-22", "2026-07-22");
        verify(polygonClient).getFinancials("ASML", "2026", "Q2");
        verify(polygonClient, never()).getPriceRange("ASML", "2026-04-22", "2026-07-22");
    }

    @Test
    void getPeriod_nonUsdWithoutConfiguredAlphaVantageTickerReturnsConfigurationWarning() throws Exception
    {
        org.kaleta.model.Company model = new org.kaleta.model.Company();
        model.setId(company.getId());
        model.setTicker("ASML");
        model.setCurrency(Currency.€);
        when(companyService.getCompany(company.getId())).thenReturn(model);

        PeriodImportDataDto result = importService.getPeriod(company.getId(), "26Q2", "2026-06");

        assertThat(result.getWarnings(), is(java.util.List.of(
                "Alpha Vantage ticker for non-USD company ASML is not configured. "
                        + "Configure it in the company edit dialog to load all Alpha Vantage suggestions.")));
        verify(alphaVantageClient, never()).getShares("ASML", "26Q2", "2026-06");
        verify(alphaVantageClient, never()).getEarnings("ASML", "26Q2", "2026-06");
    }

    private EstimateImportDto.Quarter quarter(String eps, String date)
    {
        EstimateImportDto.Quarter quarter = new EstimateImportDto.Quarter();
        quarter.setEps(eps);
        quarter.setDate(date);
        return quarter;
    }
}
