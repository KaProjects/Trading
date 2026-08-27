package org.kaleta.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.NewsSentimentLatestDto;
import org.kaleta.rest.dto.NewsSentimentPeriodDto;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsSentimentServiceTest
{
    private static final Long COMPANY_ID = 1L;
    private static final Long PERIOD_ID = 10L;

    private CompanyService companyService;
    private PeriodService periodService;
    private PeriodDao periodDao;
    private FirebaseService firebaseService;
    private NewsSentimentService service;
    private Period current;
    private Period previous;

    @BeforeEach
    void before()
    {
        companyService = mock(CompanyService.class);
        periodService = mock(PeriodService.class);
        periodDao = mock(PeriodDao.class);
        firebaseService = mock(FirebaseService.class);

        service = new NewsSentimentService();
        service.companyService = companyService;
        service.periodService = periodService;
        service.periodDao = periodDao;
        service.firebaseService = firebaseService;
        service.periodDateWindowService = new PeriodDateWindowService();

        current = period(PERIOD_ID, "25Q2", "2025-08-27");
        previous = period(9L, "25Q1", "2025-05-28");
        when(companyService.findEntity(COMPANY_ID)).thenReturn(current.getCompany());
        when(periodService.get(PERIOD_ID)).thenReturn(current);
        when(periodDao.list(COMPANY_ID)).thenReturn(List.of(current, previous));
    }

    @Test
    void getLatestMapsTheNewestValidRecord()
    {
        when(firebaseService.getLatestNewsSentiments("NVDA"))
                .thenReturn(new FirebaseService.NewsSentimentsResult(new LinkedHashMap<>(Map.of(
                        "2026-08-16-older", sentiment(Map.of("positive", 1), "Older takeaway"),
                        "2026-08-23-latest", sentiment(
                                Map.of("positive", 3, "neutral", 1),
                                "  Demand remains broad.  "))), List.of()));

        NewsSentimentLatestDto result = service.getLatest(COMPANY_ID);

        assertThat(result.record().id(), is("2026-08-23-latest"));
        assertThat(result.record().date(), is(LocalDate.parse("2026-08-23")));
        assertThat(result.record().total(), is(4));
        assertThat(result.record().stats(), is(Map.of("positive", 3, "neutral", 1)));
        assertThat(result.record().keyTakeaways(), contains("Demand remains broad."));
        assertThat(result.warnings(), is(List.of()));
    }

    @Test
    void getByPeriodUsesTheHalfOpenWindowAndSortsNewestFirst()
    {
        Map<String, FirebaseCompany.NewsSentiment> records = new LinkedHashMap<>();
        records.put("2025-05-27-before", sentiment(Map.of("negative", 1), "Before"));
        records.put("2025-05-28-start", sentiment(Map.of("positive", 2), "Start"));
        records.put("2025-08-26-last", sentiment(Map.of("mixed", 1), "Last"));
        records.put("2025-08-27-end", sentiment(Map.of("neutral", 1), "End"));
        when(firebaseService.getNewsSentiments(
                "NVDA",
                LocalDate.parse("2025-05-28"),
                LocalDate.parse("2025-08-27")))
                .thenReturn(new FirebaseService.NewsSentimentsResult(records, List.of()));

        NewsSentimentPeriodDto result = service.getByPeriod(PERIOD_ID);

        assertThat(result.records(), hasSize(2));
        assertThat(result.records().get(0).id(), is("2025-08-26-last"));
        assertThat(result.records().get(1).id(), is("2025-05-28-start"));
        assertThat(result.window().start(), is(LocalDate.parse("2025-05-28")));
        assertThat(result.window().end(), is(LocalDate.parse("2025-08-27")));
    }

    @Test
    void malformedRecordsAreSkippedAndReturnedAsWarnings()
    {
        FirebaseCompany.NewsSentiment malformed = sentiment(Map.of("positive", -1), "Invalid");
        when(firebaseService.getLatestNewsSentiments("NVDA"))
                .thenReturn(new FirebaseService.NewsSentimentsResult(
                        Map.of("not-a-date", malformed),
                        List.of("partial Firebase warning")));

        NewsSentimentLatestDto result = service.getLatest(COMPANY_ID);

        assertThat(result.record(), is(nullValue()));
        assertThat(result.warnings(), hasSize(2));
        assertThat(result.warnings().get(0), is("partial Firebase warning"));
        assertThat(result.warnings().get(1), containsString("record key does not start with YYYY-MM-DD"));
    }

    @Test
    void unavailableWindowReturnsAWarningWithoutCallingFirebase()
    {
        current.setReportDate(null);
        previous.setReportDate(null);

        NewsSentimentPeriodDto result = service.getByPeriod(PERIOD_ID);

        assertThat(result.records(), is(List.of()));
        assertThat(result.window(), is(nullValue()));
        assertThat(result.warnings().getFirst(), containsString(
                "current and previous report dates are unavailable"));
        verify(firebaseService, never()).getNewsSentiments(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private Period period(Long id, String name, String reportDate)
    {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setTicker("NVDA");

        Period period = new Period();
        period.setId(id);
        period.setCompany(company);
        period.setName(PeriodName.valueOf(name));
        period.setReportDate(reportDate == null ? null : Date.valueOf(reportDate));
        return period;
    }

    private FirebaseCompany.NewsSentiment sentiment(Map<String, Integer> values, String... takeaways)
    {
        FirebaseCompany.NewsSentiment sentiment = new FirebaseCompany.NewsSentiment();
        sentiment.setStats(values);
        sentiment.setKey_takeaways(List.of(takeaways));
        return sentiment;
    }
}
