package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.TargetStats;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.api.TargetDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.persistence.entity.Target;
import org.kaleta.rest.dto.TargetCreateDto;
import org.kaleta.rest.dto.TargetDto;
import org.kaleta.rest.dto.TargetSyncCountsDto;
import org.kaleta.rest.dto.TargetSyncDto;
import org.kaleta.rest.error.ConflictException;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class TargetServiceTest
{
    private static final Long COMPANY_ID = 1L;
    private static final Long PERIOD_ID = 10L;

    @InjectMock
    TargetDao targetDao;
    @InjectMock
    PeriodDao periodDao;
    @InjectMock
    PeriodService periodService;
    @InjectMock
    @MockitoConfig(convertScopes = true)
    FirebaseService firebaseService;
    @InjectMock
    CompanyService companyService;

    @Inject
    TargetService targetService;

    private Period current;
    private Period previous;

    @BeforeEach
    void before()
    {
        reset(targetDao, periodDao, periodService, firebaseService, companyService);
        current = period(PERIOD_ID, "25Q2", "2025-08-27");
        previous = period(9L, "25Q1", "2025-05-28");

        when(periodService.get(PERIOD_ID)).thenReturn(current);
        when(companyService.findEntity(COMPANY_ID)).thenReturn(current.getCompany());
        when(periodDao.list(COMPANY_ID)).thenReturn(List.of(current, previous));
        when(targetDao.list(PERIOD_ID)).thenReturn(List.of());
        when(firebaseService.getTargets("NVDA"))
                .thenReturn(new FirebaseService.TargetsResult(List.of(), List.of()));
    }

    @Test
    void getAll_mapsPersistedTargets()
    {
        Target entity = target(7L, current, "2025-07-10", "Northstar", "175.25");
        entity.setRating("Buy");
        entity.setOverview("Demand remains strong.");
        entity.setTakeaway1("Margins are expanding.");
        when(targetDao.list(PERIOD_ID)).thenReturn(List.of(entity));

        List<TargetDto> result = targetService.getAll(PERIOD_ID);

        assertThat(result, hasSize(1));
        assertThat(result.getFirst().getId(), is(7L));
        assertThat(result.getFirst().getPeriodId(), is(PERIOD_ID));
        assertThat(result.getFirst().getDate(), is("2025-07-10"));
        assertThat(result.getFirst().getInstitution(), is("Northstar"));
        assertBigDecimals(result.getFirst().getPrice(), new BigDecimal("175.25"));
        assertThat(result.getFirst().getRating(), is("Buy"));
        assertThat(result.getFirst().getOverview(), is("Demand remains strong."));
        assertThat(result.getFirst().getTakeaway1(), is("Margins are expanding."));
        assertThat(result.getFirst().getTakeaway2(), is(nullValue()));
    }

    @Test
    void create_normalizesOptionalFieldsAndRejectsDuplicateIdentity()
    {
        TargetCreateDto dto = createDto();
        dto.setInstitution("  Northstar  ");
        dto.setRating("  Buy  ");
        dto.setOverview("   ");

        TargetDto result = targetService.create(PERIOD_ID, dto);

        ArgumentCaptor<Target> captor = ArgumentCaptor.forClass(Target.class);
        verify(targetDao).create(captor.capture());
        Target created = captor.getValue();
        assertThat(created.getPeriod(), is(current));
        assertThat(created.getDate().toString(), is("2025-07-10"));
        assertThat(created.getInstitution(), is("Northstar"));
        assertBigDecimals(created.getPrice(), new BigDecimal("175.2500"));
        assertThat(created.getRating(), is("Buy"));
        assertThat(created.getOverview(), is(nullValue()));
        assertThat(result.getInstitution(), is("Northstar"));

        when(targetDao.findByIdentity(
                PERIOD_ID,
                Date.valueOf("2025-07-10"),
                "Northstar",
                new BigDecimal("175.2500")))
                .thenReturn(Optional.of(created));

        assertThrows(ConflictException.class, () -> targetService.create(PERIOD_ID, dto));
    }

    @ParameterizedTest
    @CsvSource({
            "2025-05-28, true",
            "2025-08-26, true",
            "2025-05-27, false",
            "2025-08-27, false"
    })
    void create_enforcesPeriodDateWindow(String date, boolean valid)
    {
        TargetCreateDto dto = createDto();
        dto.setDate(date);

        if (valid) {
            targetService.create(PERIOD_ID, dto);
            verify(targetDao).create(any(Target.class));
        } else {
            InvalidInputException exception = assertThrows(
                    InvalidInputException.class,
                    () -> targetService.create(PERIOD_ID, dto));
            assertThat(exception.getMessage(), containsString(
                    "must be on or after '2025-05-28' and before '2025-08-27'"));
            verify(targetDao, never()).create(any(Target.class));
        }
    }

    @Test
    void create_rejectsDateWhenPeriodWindowIsUnavailable()
    {
        current.setReportDate(null);
        previous.setReportDate(null);

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> targetService.create(PERIOD_ID, createDto()));

        assertThat(exception.getMessage(), containsString("target date cannot be validated"));
        verify(targetDao, never()).create(any(Target.class));
    }

    @Test
    void delete_rejectsUnknownTarget()
    {
        when(targetDao.get(7L)).thenReturn(target(7L, current, "2025-07-10", "Northstar", "175"));
        targetService.delete(7L);
        verify(targetDao).delete(7L);

        when(targetDao.get(8L)).thenThrow(NoResultException.class);
        assertThrows(InvalidInputException.class, () -> targetService.delete(8L));
        verify(targetDao, never()).delete(8L);
    }

    @Test
    void getStatistics_delegatesToDao()
    {
        List<Long> periodIds = List.of(9L, PERIOD_ID);
        Map<Long, TargetStats> expected = Map.of(
                PERIOD_ID,
                new TargetStats(2, new BigDecimal("150"), new BigDecimal("162.50"), new BigDecimal("175")));
        when(targetDao.statistics(periodIds)).thenReturn(expected);

        assertThat(targetService.getStatistics(periodIds), is(expected));
    }

    @Test
    void countImportCandidates_usesHalfOpenReportDateWindowAndExcludesPersistedIdentity()
    {
        Target persisted = target(1L, current, "2025-06-10", "Existing", "160.00");
        when(targetDao.list(PERIOD_ID)).thenReturn(List.of(persisted));
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(List.of(
                firebaseTarget("2025-05-27", "Before", "140"),
                firebaseTarget("2025-05-28", "Start included", "145"),
                firebaseTarget("2025-06-10", "Existing", "160.0000"),
                firebaseTarget("2025-08-26", "Last included", "180"),
                firebaseTarget("2025-08-27", "End excluded", "185")
        ), List.of()));

        TargetSyncDto result = targetService.countImportCandidates(PERIOD_ID);

        assertThat(result.count(), is(2));
        assertThat(result.warnings(), is(List.of()));
        verify(targetDao, never()).createAll(anyList());
    }

    @Test
    void countImportCandidatesByCompany_readsFirebaseAndPersistedTargetsOnce()
    {
        Target persisted = target(1L, current, "2025-06-10", "Existing", "160.00");
        when(targetDao.listByPeriodIds(List.of(PERIOD_ID, 9L))).thenReturn(List.of(persisted));
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(List.of(
                firebaseTarget("2025-03-15", "Previous period", "145"),
                firebaseTarget("2025-06-10", "Existing", "160.0000"),
                firebaseTarget("2025-07-10", "Current period", "180")
        ), List.of()));

        TargetSyncCountsDto result = targetService.countImportCandidatesByCompany(COMPANY_ID);

        assertThat(result.counts(), is(Map.of(9L, 1, PERIOD_ID, 1)));
        assertThat(result.failedPeriodIds(), is(Set.of()));
        assertThat(result.warnings(), is(List.of()));
        verify(firebaseService, times(1)).getTargets("NVDA");
        verify(targetDao, times(1)).listByPeriodIds(List.of(PERIOD_ID, 9L));
    }

    @Test
    void countImportCandidatesByCompany_marksEveryPeriodWhenFirebaseFails()
    {
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(
                List.of(),
                List.of("Firebase targets for NVDA could not be loaded: permission denied")));

        TargetSyncCountsDto result = targetService.countImportCandidatesByCompany(COMPANY_ID);

        assertThat(result.counts(), is(Map.of(9L, 0, PERIOD_ID, 0)));
        assertThat(result.failedPeriodIds(), is(Set.of(9L, PERIOD_ID)));
        assertThat(result.warnings(), contains("Firebase targets for NVDA could not be loaded: permission denied"));
    }

    @Test
    void sync_insertsOnlyMissingTargetsAndMapsReport()
    {
        FirebaseCompany.Gemini.Target source = firebaseTarget("2025-06-10", "Northstar", "175.25");
        source.setRating("Outperform");
        FirebaseCompany.Gemini.Target.Report report = new FirebaseCompany.Gemini.Target.Report();
        report.setOverview("  Strong product cycle.  ");
        report.setKey_takeaways(List.of("Revenue accelerates", "Margins improve"));
        source.setReport(report);
        when(firebaseService.getTargets("NVDA"))
                .thenReturn(new FirebaseService.TargetsResult(List.of(source), List.of("partial Firebase warning")));

        TargetSyncDto result = targetService.sync(PERIOD_ID);

        ArgumentCaptor<List<Target>> captor = ArgumentCaptor.forClass(List.class);
        verify(targetDao).createAll(captor.capture());
        assertThat(captor.getValue(), hasSize(1));
        Target created = captor.getValue().getFirst();
        assertThat(created.getPeriod(), is(current));
        assertThat(created.getInstitution(), is("Northstar"));
        assertThat(created.getRating(), is("Outperform"));
        assertThat(created.getOverview(), is("Strong product cycle."));
        assertThat(created.getTakeaway1(), is("Revenue accelerates"));
        assertThat(created.getTakeaway2(), is("Margins improve"));
        assertThat(created.getTakeaway3(), is(nullValue()));
        assertThat(result.count(), is(1));
        assertThat(result.warnings(), contains("partial Firebase warning"));
    }

    @Test
    void countImportCandidates_usesCurrentDateMinusThreeMonthsWhenPreviousDateIsUnavailable()
    {
        when(periodDao.list(COMPANY_ID)).thenReturn(List.of(current));
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(List.of(
                firebaseTarget("2025-05-26", "Before", "140"),
                firebaseTarget("2025-05-27", "Start included", "145")
        ), List.of()));

        assertThat(targetService.countImportCandidates(PERIOD_ID).count(), is(1));
    }

    @Test
    void countImportCandidates_usesPreviousDatePlusThreeMonthsWhenCurrentDateIsUnavailable()
    {
        current.setReportDate(null);
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(List.of(
                firebaseTarget("2025-08-27", "Last included", "180"),
                firebaseTarget("2025-08-28", "End excluded", "185")
        ), List.of()));

        assertThat(targetService.countImportCandidates(PERIOD_ID).count(), is(1));
    }

    @Test
    void countImportCandidates_disablesSyncWhenBothBoundaryDatesAreUnavailable()
    {
        current.setReportDate(null);
        previous.setReportDate(null);

        TargetSyncDto result = targetService.countImportCandidates(PERIOD_ID);

        assertThat(result.count(), is(0));
        assertThat(result.warnings(), hasSize(1));
        assertThat(result.warnings().getFirst(), containsString("current and previous report dates are unavailable"));
        verify(firebaseService, never()).getTargets(any());
    }

    @Test
    void countImportCandidates_returnsFirebaseWarningsWithoutFailing()
    {
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(
                List.of(),
                List.of("Firebase targets for NVDA could not be loaded: permission denied")));

        TargetSyncDto result = targetService.countImportCandidates(PERIOD_ID);

        assertThat(result.count(), is(0));
        assertThat(result.warnings(), contains("Firebase targets for NVDA could not be loaded: permission denied"));
    }

    @Test
    void countImportCandidates_skipsFirebaseTargetsExceedingDatabaseLengths()
    {
        FirebaseCompany.Gemini.Target oversizedInstitution = firebaseTarget(
                "2025-06-10",
                "I".repeat(51),
                "150");
        FirebaseCompany.Gemini.Target oversizedOverview = firebaseTarget(
                "2025-06-11",
                "Valid institution",
                "151");
        FirebaseCompany.Gemini.Target.Report report = new FirebaseCompany.Gemini.Target.Report();
        report.setOverview("O".repeat(1001));
        oversizedOverview.setReport(report);
        FirebaseCompany.Gemini.Target oversizedTakeaway = firebaseTarget(
                "2025-06-12",
                "Another institution",
                "152");
        FirebaseCompany.Gemini.Target.Report takeawayReport = new FirebaseCompany.Gemini.Target.Report();
        takeawayReport.setKey_takeaways(List.of("T".repeat(501)));
        oversizedTakeaway.setReport(takeawayReport);
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(
                List.of(oversizedInstitution, oversizedOverview, oversizedTakeaway),
                List.of()));

        TargetSyncDto result = targetService.countImportCandidates(PERIOD_ID);

        assertThat(result.count(), is(0));
        assertThat(result.warnings(), hasSize(3));
        assertThat(result.warnings().toString(), containsString("institution is longer than 50 characters"));
        assertThat(result.warnings().toString(), containsString("overview is longer than 1000 characters"));
        assertThat(result.warnings().toString(), containsString("takeaway 1 is longer than 500 characters"));
    }

    @ParameterizedTest
    @CsvSource({
            "25Q1,24Q4",
            "25Q2,25Q1",
            "25Q3,25Q2",
            "25Q4,25Q3",
            "25H1,24H2",
            "25H2,25H1",
            "25FY,24FY"
    })
    void countImportCandidates_findsPreviousPeriodOfSameCadence(String currentName, String previousName)
    {
        current.setName(PeriodName.valueOf(currentName));
        current.setReportDate(Date.valueOf("2025-08-01"));
        previous.setName(PeriodName.valueOf(previousName));
        previous.setReportDate(Date.valueOf("2025-03-01"));
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(
                List.of(firebaseTarget("2025-04-01", "Inside", "150")),
                List.of()));

        assertThat(targetService.countImportCandidates(PERIOD_ID).count(), is(1));
    }

    private TargetCreateDto createDto()
    {
        TargetCreateDto dto = new TargetCreateDto();
        dto.setDate("2025-07-10");
        dto.setInstitution("Northstar");
        dto.setPrice("175.2500");
        dto.setRating("Buy");
        dto.setOverview("Overview");
        dto.setTakeaway1("Takeaway");
        return dto;
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
        period.setEndingMonth(YearMonth.of(2025, 7));
        period.setReportDate(reportDate == null ? null : Date.valueOf(reportDate));
        return period;
    }

    private Target target(
            Long id,
            Period period,
            String date,
            String institution,
            String price)
    {
        Target target = new Target();
        target.setId(id);
        target.setPeriod(period);
        target.setDate(Date.valueOf(date));
        target.setInstitution(institution);
        target.setPrice(new BigDecimal(price));
        return target;
    }

    private FirebaseCompany.Gemini.Target firebaseTarget(
            String date,
            String institution,
            String price)
    {
        FirebaseCompany.Gemini.Target target = new FirebaseCompany.Gemini.Target();
        target.setDate(date);
        target.setInstitution(institution);
        target.setPrice(price);
        return target;
    }
}
