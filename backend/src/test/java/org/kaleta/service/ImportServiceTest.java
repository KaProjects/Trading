package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Generator;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.EstimateImportDto;
import org.kaleta.rest.error.InvalidInputException;

import java.time.YearMonth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.reset;
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

    @Inject
    ImportService importService;

    private Company company;
    private Period period;

    @BeforeEach
    void before()
    {
        reset(companyService, periodService, firebaseService);
        company = Generator.generateCompany(100L);
        company.setTicker("NVDA");
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

    private EstimateImportDto.Quarter quarter(String eps, String date)
    {
        EstimateImportDto.Quarter quarter = new EstimateImportDto.Quarter();
        quarter.setEps(eps);
        quarter.setDate(date);
        return quarter;
    }
}
