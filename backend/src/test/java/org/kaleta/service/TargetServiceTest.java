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
import org.kaleta.model.Periods;
import org.kaleta.model.TargetStats;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.api.TargetDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.persistence.entity.Target;
import org.kaleta.rest.dto.ActionableCompanyDto;
import org.kaleta.rest.dto.PeriodImportCandidateDto;
import org.kaleta.rest.dto.TargetCandidatesDto;
import org.kaleta.rest.dto.TargetCreateDto;
import org.kaleta.rest.dto.TargetDto;
import org.kaleta.rest.dto.TargetImportDto;
import org.kaleta.rest.dto.TargetImportResultDto;
import org.kaleta.rest.dto.TargetSyncCountsDto;
import org.kaleta.rest.dto.TargetSyncDto;
import org.kaleta.rest.error.ConflictException;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
    void delete_alsoRemovesTheMatchingTargetFromFirebase()
    {
        when(targetDao.get(7L)).thenReturn(target(7L, current, "2025-07-10", "Northstar", "175"));

        targetService.delete(7L);

        verify(firebaseService).deleteTarget("NVDA", LocalDate.parse("2025-07-10"), "Northstar", new BigDecimal("175"));
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
    void getCompaniesWithImportCandidates_countsImportablePeriodsAndTargetsUsingOneBulkFirebaseCall()
    {
        CompanyWithStats nvda = new CompanyWithStats();
        nvda.setId(COMPANY_ID);
        nvda.setTicker("NVDA");
        CompanyWithStats amd = new CompanyWithStats();
        amd.setId(2L);
        amd.setTicker("AMD");
        CompanyWithStats tsla = new CompanyWithStats();
        tsla.setId(3L);
        tsla.setTicker("TSLA");
        when(companyService.getAllWithStats()).thenReturn(List.of(nvda, amd, tsla));

        FirebaseCompany nvdaFirebase = new FirebaseCompany();
        FirebaseCompany.Gemini nvdaGemini = new FirebaseCompany.Gemini();
        nvdaGemini.setTargets(Map.of("t1", firebaseTarget("2025-07-10", "Current period", "180")));
        nvdaFirebase.setGemini(nvdaGemini);

        FirebaseCompany amdFirebase = new FirebaseCompany();
        FirebaseCompany.Gemini amdGemini = new FirebaseCompany.Gemini();
        FirebaseCompany.Gemini.Quarter amdQuarter = new FirebaseCompany.Gemini.Quarter();
        amdQuarter.setEnding_month("25-09");
        amdGemini.setQuarters(Map.of("25Q3", amdQuarter));
        amdFirebase.setGemini(amdGemini);

        when(firebaseService.getAllCompanies()).thenReturn(new FirebaseService.AllCompaniesResult(
                Map.of("NVDA", nvdaFirebase, "AMD", amdFirebase), List.of()));
        when(firebaseService.getNewerPeriods(nvdaFirebase, "25Q2"))
                .thenReturn(new FirebaseService.ImportCandidatesResult(List.of(), List.of()));
        when(firebaseService.getTargets(nvdaFirebase)).thenReturn(new FirebaseService.TargetsResult(
                List.of(firebaseTarget("2025-07-10", "Current period", "180")), List.of()));
        PeriodImportCandidateDto amdCandidate = new PeriodImportCandidateDto();
        amdCandidate.setName("25Q3");
        amdCandidate.setEndingMonth("2025-09");
        amdCandidate.setIsReported(false);
        when(firebaseService.getNewerPeriods(amdFirebase, null))
                .thenReturn(new FirebaseService.ImportCandidatesResult(List.of(amdCandidate), List.of()));
        when(firebaseService.getTargets(amdFirebase))
                .thenReturn(new FirebaseService.TargetsResult(List.of(), List.of()));
        when(firebaseService.getNewerPeriods((FirebaseCompany) null, null))
                .thenReturn(new FirebaseService.ImportCandidatesResult(List.of(), List.of()));
        when(firebaseService.getTargets((FirebaseCompany) null))
                .thenReturn(new FirebaseService.TargetsResult(List.of(), List.of()));

        Periods.Period nvdaPeriod = new Periods.Period();
        nvdaPeriod.setName(PeriodName.valueOf("25Q2"));
        Periods nvdaPeriods = new Periods();
        nvdaPeriods.getPeriods().add(nvdaPeriod);
        when(periodService.getBy(COMPANY_ID)).thenReturn(nvdaPeriods);
        when(periodService.getBy(2L)).thenReturn(new Periods());
        when(periodService.getBy(3L)).thenReturn(new Periods());

        Company amdCompany = new Company();
        amdCompany.setId(2L);
        amdCompany.setTicker("AMD");
        when(companyService.findEntity(2L)).thenReturn(amdCompany);
        when(periodDao.list(2L)).thenReturn(List.of());
        Company tslaCompany = new Company();
        tslaCompany.setId(3L);
        tslaCompany.setTicker("TSLA");
        when(companyService.findEntity(3L)).thenReturn(tslaCompany);
        when(periodDao.list(3L)).thenReturn(List.of());

        List<ActionableCompanyDto> result = targetService.getCompaniesWithImportCandidates();

        ActionableCompanyDto expectedNvda = new ActionableCompanyDto();
        expectedNvda.setCompany(nvda);
        expectedNvda.setImportablePeriodsCount(0);
        expectedNvda.setImportableTargetsCount(1);
        ActionableCompanyDto expectedAmd = new ActionableCompanyDto();
        expectedAmd.setCompany(amd);
        expectedAmd.setImportablePeriodsCount(1);
        expectedAmd.setImportableTargetsCount(0);
        assertThat(result, containsInAnyOrder(expectedNvda, expectedAmd));

        verify(firebaseService, times(1)).getAllCompanies();
        verify(firebaseService, never()).getNewerPeriods(anyString(), any());
        verify(firebaseService, never()).getTargets(anyString());
    }

    @Test
    void importCandidates_insertsSelectedTargetAndMapsReport()
    {
        FirebaseCompany.Gemini.Target source = firebaseTarget("2025-06-10", "Northstar", "175.25");
        source.setRating("Outperform");
        FirebaseCompany.Gemini.Target.Report report = new FirebaseCompany.Gemini.Target.Report();
        report.setOverview("  Strong product cycle.  ");
        report.setKey_takeaways(List.of("Revenue accelerates", "Margins improve"));
        source.setReport(report);
        when(firebaseService.getTargets("NVDA"))
                .thenReturn(new FirebaseService.TargetsResult(List.of(source), List.of("partial Firebase warning")));

        TargetImportResultDto result = targetService.importCandidates(
                PERIOD_ID,
                importDto(List.of(selection("2025-06-10", "Northstar", "175.25")), List.of()));

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
        assertThat(result.imported(), is(1));
        assertThat(result.discarded(), is(0));
        assertThat(result.warnings(), contains("partial Firebase warning"));
    }

    @Test
    void getImportCandidates_listsEveryCandidateWithItsDetails()
    {
        FirebaseCompany.Gemini.Target source = firebaseTarget("2025-06-10", "Northstar", "175.25");
        source.setRating("Outperform");
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(
                List.of(source, firebaseTarget("2025-06-11", "Southpeak", "180")),
                List.of()));

        TargetCandidatesDto result = targetService.getImportCandidates(PERIOD_ID);

        assertThat(result.candidates(), hasSize(2));
        TargetCandidatesDto.Candidate first = result.candidates().getFirst();
        assertThat(first.date(), is("2025-06-10"));
        assertThat(first.institution(), is("Northstar"));
        assertBigDecimals(first.price(), new BigDecimal("175.25"));
        assertThat(first.rating(), is("Outperform"));
        assertThat(result.candidates().get(1).institution(), is("Southpeak"));
    }

    @Test
    void importCandidates_importsSelectedAndRemovesDiscardedFromFirebase()
    {
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(List.of(
                firebaseTarget("2025-06-10", "Northstar", "175.25"),
                firebaseTarget("2025-06-11", "Southpeak", "180")
        ), List.of()));

        TargetImportResultDto result = targetService.importCandidates(
                PERIOD_ID,
                importDto(
                        List.of(selection("2025-06-10", "Northstar", "175.25")),
                        List.of(selection("2025-06-11", "Southpeak", "180"))));

        ArgumentCaptor<List<Target>> captor = ArgumentCaptor.forClass(List.class);
        verify(targetDao).createAll(captor.capture());
        assertThat(captor.getValue(), hasSize(1));
        assertThat(captor.getValue().getFirst().getInstitution(), is("Northstar"));

        verify(firebaseService).deleteTarget(
                "NVDA",
                LocalDate.parse("2025-06-11"),
                "Southpeak",
                new BigDecimal("180"));
        verify(firebaseService, never()).deleteTarget(
                anyString(),
                any(),
                org.mockito.ArgumentMatchers.eq("Northstar"),
                any());

        assertThat(result.imported(), is(1));
        assertThat(result.discarded(), is(1));
    }

    @Test
    void importCandidates_leavesCandidatesThatWereNotReviewedUntouched()
    {
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(List.of(
                firebaseTarget("2025-06-10", "Northstar", "175.25"),
                firebaseTarget("2025-06-11", "Appeared Later", "180")
        ), List.of()));

        TargetImportResultDto result = targetService.importCandidates(
                PERIOD_ID,
                importDto(List.of(selection("2025-06-10", "Northstar", "175.25")), List.of()));

        assertThat(result.imported(), is(1));
        assertThat(result.discarded(), is(0));
        verify(firebaseService, never()).deleteTarget(anyString(), any(), anyString(), any());
    }

    private TargetImportDto importDto(
            List<TargetImportDto.Selection> selected,
            List<TargetImportDto.Selection> discarded)
    {
        TargetImportDto dto = new TargetImportDto();
        dto.setSelected(selected);
        dto.setDiscarded(discarded);
        return dto;
    }

    private TargetImportDto.Selection selection(String date, String institution, String price)
    {
        TargetImportDto.Selection selection = new TargetImportDto.Selection();
        selection.setDate(date);
        selection.setInstitution(institution);
        selection.setPrice(new BigDecimal(price));
        return selection;
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

    @ParameterizedTest
    @CsvSource({
            "25H2,2025-02-27,2025-02-26",
            "25FY,2024-08-27,2024-08-26"
    })
    void countImportCandidates_usesFrequencyAwareFallbackWhenPreviousDateIsUnavailable(
            String periodName,
            String startDate,
            String beforeDate)
    {
        current.setName(PeriodName.valueOf(periodName));
        when(periodDao.list(COMPANY_ID)).thenReturn(List.of(current));
        when(firebaseService.getTargets("NVDA")).thenReturn(new FirebaseService.TargetsResult(List.of(
                firebaseTarget(beforeDate, "Before", "140"),
                firebaseTarget(startDate, "Start included", "145")
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
        verify(firebaseService, never()).getTargets(anyString());
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
