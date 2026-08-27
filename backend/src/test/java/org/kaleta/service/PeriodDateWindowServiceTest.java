package org.kaleta.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class PeriodDateWindowServiceTest
{
    private final PeriodDateWindowService service = new PeriodDateWindowService();

    @Test
    void usesPreviousAndCurrentReportDatesAsHalfOpenBoundaries()
    {
        Period current = period("25Q2", "2025-08-27");
        Period previous = period("25Q1", "2025-05-28");

        PeriodDateWindowService.Resolution result = service.resolve(current, List.of(current, previous));

        assertThat(result.error(), is(nullValue()));
        assertThat(result.window().start(), is(LocalDate.parse("2025-05-28")));
        assertThat(result.window().end(), is(LocalDate.parse("2025-08-27")));
        assertThat(result.window().contains(LocalDate.parse("2025-05-28")), is(true));
        assertThat(result.window().contains(LocalDate.parse("2025-08-27")), is(false));
    }

    @ParameterizedTest
    @CsvSource({
            "25Q2,2025-08-27,2025-05-27",
            "25H2,2025-08-27,2025-02-27",
            "25FY,2025-08-27,2024-08-27"
    })
    void derivesMissingStartFromPeriodFrequency(String name, String reportDate, String expectedStart)
    {
        Period current = period(name, reportDate);

        PeriodDateWindowService.Resolution result = service.resolve(current, List.of(current));

        assertThat(result.window().start(), is(LocalDate.parse(expectedStart)));
        assertThat(result.window().end(), is(LocalDate.parse(reportDate)));
    }

    @ParameterizedTest
    @CsvSource({
            "25Q2,25Q1,2025-05-28,2025-08-28",
            "25H2,25H1,2025-05-28,2025-11-28",
            "25FY,24FY,2025-05-28,2026-05-28"
    })
    void derivesMissingEndFromPeriodFrequency(
            String currentName,
            String previousName,
            String previousReportDate,
            String expectedEnd)
    {
        Period current = period(currentName, null);
        Period previous = period(previousName, previousReportDate);

        PeriodDateWindowService.Resolution result = service.resolve(current, List.of(current, previous));

        assertThat(result.window().start(), is(LocalDate.parse(previousReportDate)));
        assertThat(result.window().end(), is(LocalDate.parse(expectedEnd)));
    }

    @Test
    void ignoresAPreviousPeriodWithDifferentCadence()
    {
        Period current = period("25H2", "2025-08-27");
        Period unrelatedQuarter = period("25Q2", "2025-07-24");

        PeriodDateWindowService.Resolution result = service.resolve(
                current,
                List.of(current, unrelatedQuarter));

        assertThat(result.window().start(), is(LocalDate.parse("2025-02-27")));
    }

    @Test
    void returnsAnUnavailableResolutionWhenBothDatesAreMissing()
    {
        PeriodDateWindowService.Resolution result = service.resolve(
                period("25Q2", null),
                List.of());

        assertThat(result.window(), is(nullValue()));
        assertThat(result.error(), is("current and previous report dates are unavailable"));
    }

    private Period period(String name, String reportDate)
    {
        Company company = new Company();
        company.setId(1L);

        Period period = new Period();
        period.setCompany(company);
        period.setName(PeriodName.valueOf(name));
        period.setReportDate(reportDate == null ? null : Date.valueOf(reportDate));
        return period;
    }
}
