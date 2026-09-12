package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.model.Periods;
import org.kaleta.persistence.api.LatestDao;
import org.kaleta.persistence.api.TargetDao;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.persistence.entity.Target;
import org.kaleta.rest.dto.EpsOutperformerDto;
import org.kaleta.rest.dto.MarginOutperformerDto;
import org.kaleta.rest.dto.OutperformersDto;
import org.kaleta.rest.dto.SentimentOutperformerDto;
import org.kaleta.rest.dto.TargetOutperformerDto;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@QuarkusTest
class OutperformersServiceTest
{
    @InjectMock
    CompanyService companyService;
    @InjectMock
    PeriodService periodService;
    @InjectMock
    EstimateService estimateService;
    @InjectMock
    TargetDao targetDao;
    @InjectMock
    LatestDao latestDao;
    @InjectMock
    @MockitoConfig(convertScopes = true)
    FirebaseService firebaseService;

    @Inject
    OutperformersService outperformersService;

    private CompanyWithStats company;

    @BeforeEach
    void before()
    {
        reset(companyService, periodService, estimateService, targetDao, latestDao, firebaseService);

        company = new CompanyWithStats();
        company.setId(1L);
        company.setTicker("NVDA");
        when(companyService.getAllWithStats()).thenReturn(List.of(company));
        when(firebaseService.getAllCompanies())
                .thenReturn(new FirebaseService.AllCompaniesResult(Map.of(), List.of()));
        when(periodService.getBy(1L)).thenReturn(new Periods());
        when(targetDao.listByPeriodIds(List.of())).thenReturn(List.of());
    }

    @Test
    void get_excludesCompanyWithNoPeriodsFromEveryList()
    {
        OutperformersDto result = outperformersService.get();

        assertThat(result.getEpsEstimates(), is(empty()));
        assertThat(result.getEpsEstimatesDisqualified(), is(empty()));
        assertThat(result.getMargins(), is(empty()));
        assertThat(result.getMarginsDisqualified(), is(empty()));
        assertThat(result.getSentiment(), is(empty()));
        assertThat(result.getSentimentDisqualified(), is(empty()));
        assertThat(result.getTargets(), is(empty()));
        assertThat(result.getTargetsDisqualified(), is(empty()));
    }

    @Test
    void get_includesEpsCompanyAndComputesCumulativeChanges()
    {
        Periods periods = periodsWithLatest("25Q2");
        when(periodService.getBy(1L)).thenReturn(periods);
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(List.of());

        PeriodEstimates estimates = estimates(LocalDateTime.now().minusDays(10));
        when(estimateService.getLatest(10L, Estimate.EPS)).thenReturn(Optional.of(estimates));

        OutperformersDto result = outperformersService.get();

        assertThat(result.getEpsEstimates(), hasSize(1));
        EpsOutperformerDto entry = result.getEpsEstimates().getFirst();
        assertThat(entry.getTicker(), is("NVDA"));
        // baseline (past4..past1) = 4+4+4+4=16
        // +1Q (past3,past2,past1,current)=4+4+4+5=17 -> +6.25%
        assertThat(entry.getQuarter1Change().doubleValue(), is(6.25));
        // +2Q (past2,past1,current,next1)=4+4+5+6=19 -> +18.75%
        assertThat(entry.getQuarter2Change().doubleValue(), is(18.75));
        // +3Q (past1,current,next1,next2)=4+5+6+7=22 -> +37.5%
        assertThat(entry.getQuarter3Change().doubleValue(), is(37.5));
        // +4Q (current,next1,next2,next3)=5+6+7+8=26 -> +62.5%
        assertThat(entry.getQuarter4Change().doubleValue(), is(62.5));
        assertThat(result.getEpsEstimatesDisqualified(), is(empty()));
    }

    @Test
    void get_computesPartialChangesWhenNext2AndNext3AreMissing()
    {
        Periods periods = periodsWithLatest("25Q2");
        when(periodService.getBy(1L)).thenReturn(periods);
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(List.of());

        PeriodEstimates estimates = estimates(LocalDateTime.now().minusDays(10));
        estimates.setNext2(null);
        estimates.setNext3(null);
        when(estimateService.getLatest(10L, Estimate.EPS)).thenReturn(Optional.of(estimates));

        OutperformersDto result = outperformersService.get();

        assertThat(result.getEpsEstimates(), hasSize(1));
        EpsOutperformerDto entry = result.getEpsEstimates().getFirst();
        assertThat(entry.getQuarter1Change().doubleValue(), is(6.25));
        assertThat(entry.getQuarter2Change().doubleValue(), is(18.75));
        assertThat(entry.getQuarter3Change(), is(nullValue()));
        assertThat(entry.getQuarter4Change(), is(nullValue()));
        assertThat(result.getEpsEstimatesDisqualified(), is(empty()));
    }

    @Test
    void get_disqualifiesEpsCompanyWithStaleEstimate()
    {
        Periods periods = periodsWithLatest("25Q2");
        when(periodService.getBy(1L)).thenReturn(periods);
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(List.of());
        when(estimateService.getLatest(10L, Estimate.EPS))
                .thenReturn(Optional.of(estimates(LocalDateTime.now().minusMonths(4))));

        OutperformersDto result = outperformersService.get();

        assertThat(result.getEpsEstimates(), is(empty()));
        assertThat(result.getEpsEstimatesDisqualified(), hasSize(1));
        assertThat(result.getEpsEstimatesDisqualified().getFirst().getTicker(), is("NVDA"));
        assertThat(result.getEpsEstimatesDisqualified().getFirst().getReason(), containsString("stale"));
    }

    @Test
    void get_excludesEpsCompanyWithoutEstimatesEntirely()
    {
        Periods periods = periodsWithLatest("25Q2");
        when(periodService.getBy(1L)).thenReturn(periods);
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(List.of());
        when(estimateService.getLatest(10L, Estimate.EPS)).thenReturn(Optional.empty());

        OutperformersDto result = outperformersService.get();

        assertThat(result.getEpsEstimates(), is(empty()));
        assertThat(result.getEpsEstimatesDisqualified(), is(empty()));
    }

    @Test
    void get_includesMarginsForQuarterlyReporterWithFourConsecutiveQuartersUsingTtm()
    {
        Periods periods = periodsWithConsecutiveFinancials("25Q2", LocalDate.now().minusDays(10));
        when(periodService.getBy(1L)).thenReturn(periods);
        when(targetDao.listByPeriodIds(List.of(10L, 11L, 12L, 13L))).thenReturn(List.of());

        OutperformersDto result = outperformersService.get();

        assertThat(result.getMargins(), hasSize(1));
        MarginOutperformerDto entry = result.getMargins().getFirst();
        assertThat(entry.getTicker(), is("NVDA"));
        assertThat(entry.getRevenue().doubleValue(), is(1000.0));
        assertThat(entry.getGrossMargin().doubleValue(), is(60.0));
        assertThat(entry.getOperatingMargin().doubleValue(), is(30.0));
        assertThat(entry.getNetMargin().doubleValue(), is(20.0));
        assertThat(result.getMarginsDisqualified(), is(empty()));
    }

    @Test
    void get_disqualifiesNonQuarterlyReporterFromMargins()
    {
        Periods periods = periodWithSingleFinancial("25FY", LocalDate.now().minusDays(10));
        when(periodService.getBy(1L)).thenReturn(periods);
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(List.of());

        OutperformersDto result = outperformersService.get();

        assertThat(result.getMargins(), is(empty()));
        assertThat(result.getMarginsDisqualified(), hasSize(1));
        assertThat(result.getMarginsDisqualified().getFirst().getReason(), containsString("consecutive quarterly"));
    }

    @Test
    void get_disqualifiesCompanyWithFewerThanFourConsecutiveReportedQuarters()
    {
        Periods periods = periodWithSingleFinancial("25Q2", LocalDate.now().minusDays(10));
        when(periodService.getBy(1L)).thenReturn(periods);
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(List.of());

        OutperformersDto result = outperformersService.get();

        assertThat(result.getMargins(), is(empty()));
        assertThat(result.getMarginsDisqualified(), hasSize(1));
        assertThat(result.getMarginsDisqualified().getFirst().getReason(),
                containsString("fewer than 4 consecutive quarterly reports available (1)"));
    }

    @Test
    void get_excludesCompanyFromMarginsWhenNoPeriodHasFinancials()
    {
        Periods periods = periodsWithLatest("25Q2");
        when(periodService.getBy(1L)).thenReturn(periods);
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(List.of());

        OutperformersDto result = outperformersService.get();

        assertThat(result.getMargins(), is(empty()));
        assertThat(result.getMarginsDisqualified(), is(empty()));
    }

    @Test
    void get_disqualifiesMarginsWhenFinancialDataIsStale()
    {
        Periods periods = periodsWithConsecutiveFinancials("25Q2", LocalDate.now().minusMonths(4));
        when(periodService.getBy(1L)).thenReturn(periods);
        when(targetDao.listByPeriodIds(List.of(10L, 11L, 12L, 13L))).thenReturn(List.of());

        OutperformersDto result = outperformersService.get();

        assertThat(result.getMargins(), is(empty()));
        assertThat(result.getMarginsDisqualified(), hasSize(1));
        assertThat(result.getMarginsDisqualified().getFirst().getReason(), containsString("stale"));
    }

    @Test
    void get_includesSentimentWithinWindowAndSumsWeightedScore()
    {
        FirebaseCompany firebaseCompany = new FirebaseCompany();
        firebaseCompany.setPgn(Map.of(
                LocalDate.now().minusDays(5) + "-a", sentiment(Map.of("positive", 3, "neutral", 1, "negative", 1)),
                LocalDate.now().minusDays(40) + "-old", sentiment(Map.of("positive", 10))
        ));
        when(firebaseService.getAllCompanies())
                .thenReturn(new FirebaseService.AllCompaniesResult(Map.of("NVDA", firebaseCompany), List.of()));

        OutperformersDto result = outperformersService.get();

        assertThat(result.getSentiment(), hasSize(1));
        SentimentOutperformerDto entry = result.getSentiment().getFirst();
        assertThat(entry.getTicker(), is("NVDA"));
        assertThat(entry.getArticleCount(), is(5));
        assertThat(entry.getPositiveCount(), is(3));
        assertThat(entry.getNeutralCount(), is(1));
        assertThat(entry.getNegativeCount(), is(1));
        assertThat(entry.getWeightedSentimentSum(), is(2 * 3 + 1 * 1 + 0 * 1));
        // score = weightedSum / sqrt(articleCount) = 7 / sqrt(5) = 3.13
        assertThat(entry.getSentimentScore().doubleValue(), is(3.13));
        assertThat(result.getSentimentDisqualified(), is(empty()));
    }

    @Test
    void get_disqualifiesSentimentWithTooFewArticlesInWindow()
    {
        FirebaseCompany firebaseCompany = new FirebaseCompany();
        firebaseCompany.setPgn(Map.of(
                LocalDate.now().minusDays(2) + "-a", sentiment(Map.of("positive", 2, "neutral", 1))));
        when(firebaseService.getAllCompanies())
                .thenReturn(new FirebaseService.AllCompaniesResult(Map.of("NVDA", firebaseCompany), List.of()));

        OutperformersDto result = outperformersService.get();

        assertThat(result.getSentiment(), is(empty()));
        assertThat(result.getSentimentDisqualified(), hasSize(1));
        assertThat(result.getSentimentDisqualified().getFirst().getReason(), containsString("only 3 articles"));
    }

    @Test
    void get_excludesCompanyWithoutAnySentimentData()
    {
        when(firebaseService.getAllCompanies())
                .thenReturn(new FirebaseService.AllCompaniesResult(Map.of(), List.of()));

        OutperformersDto result = outperformersService.get();

        assertThat(result.getSentiment(), is(empty()));
        assertThat(result.getSentimentDisqualified(), is(empty()));
    }

    @Test
    void get_includesTargetsWithFreshPriceAndEnoughRecentTargets()
    {
        Periods periods = periodsWithLatest("25Q2");
        when(periodService.getBy(1L)).thenReturn(periods);
        List<Target> targets = List.of(
                target(LocalDate.now().minusDays(10), "180"),
                target(LocalDate.now().minusDays(20), "190"),
                target(LocalDate.now().minusDays(30), "200"),
                target(LocalDate.now().minusDays(40), "210"),
                target(LocalDate.now().minusDays(50), "220"));
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(targets);
        when(latestDao.list(1L)).thenReturn(List.of(
                new Latest(company, LocalDateTime.now().minusDays(1), new BigDecimal("200"))));

        OutperformersDto result = outperformersService.get();

        assertThat(result.getTargets(), hasSize(1));
        TargetOutperformerDto entry = result.getTargets().getFirst();
        assertThat(entry.getTicker(), is("NVDA"));
        assertThat(entry.getPrice().doubleValue(), is(200.0));
        // average target = (180+190+200+210+220)/5 = 200, price = 200 -> 0% diff
        assertThat(entry.getAverageTarget().doubleValue(), is(200.0));
        assertThat(entry.getPercentDiff().doubleValue(), is(0.0));
        assertThat(result.getTargetsDisqualified(), is(empty()));
    }

    @Test
    void get_disqualifiesTargetsWithFewerThanFiveInWindow()
    {
        Periods periods = periodsWithLatest("25Q2");
        when(periodService.getBy(1L)).thenReturn(periods);
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(List.of(
                target(LocalDate.now().minusDays(10), "180"),
                target(LocalDate.now().minusMonths(6), "150")));

        OutperformersDto result = outperformersService.get();

        assertThat(result.getTargets(), is(empty()));
        assertThat(result.getTargetsDisqualified(), hasSize(1));
        assertThat(result.getTargetsDisqualified().getFirst().getReason(), containsString("only 1 targets"));
    }

    @Test
    void get_disqualifiesTargetsWhenCachedPriceIsStale()
    {
        Periods periods = periodsWithLatest("25Q2");
        when(periodService.getBy(1L)).thenReturn(periods);
        List<Target> targets = List.of(
                target(LocalDate.now().minusDays(10), "180"),
                target(LocalDate.now().minusDays(20), "190"),
                target(LocalDate.now().minusDays(30), "200"),
                target(LocalDate.now().minusDays(40), "210"),
                target(LocalDate.now().minusDays(50), "220"));
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(targets);
        when(latestDao.list(1L)).thenReturn(List.of(
                new Latest(company, LocalDateTime.now().minusDays(10), new BigDecimal("200"))));

        OutperformersDto result = outperformersService.get();

        assertThat(result.getTargets(), is(empty()));
        assertThat(result.getTargetsDisqualified(), hasSize(1));
        assertThat(result.getTargetsDisqualified().getFirst().getReason(), containsString("price is stale"));
    }

    @Test
    void get_disqualifiesTargetsWithoutAnyCachedPrice()
    {
        Periods periods = periodsWithLatest("25Q2");
        when(periodService.getBy(1L)).thenReturn(periods);
        List<Target> targets = List.of(
                target(LocalDate.now().minusDays(10), "180"),
                target(LocalDate.now().minusDays(20), "190"),
                target(LocalDate.now().minusDays(30), "200"),
                target(LocalDate.now().minusDays(40), "210"),
                target(LocalDate.now().minusDays(50), "220"));
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(targets);
        when(latestDao.list(1L)).thenReturn(List.of());

        OutperformersDto result = outperformersService.get();

        assertThat(result.getTargets(), is(empty()));
        assertThat(result.getTargetsDisqualified(), hasSize(1));
        assertThat(result.getTargetsDisqualified().getFirst().getReason(), containsString("no cached price"));
    }

    @Test
    void get_sortsEachListByItsDefaultColumnDescending()
    {
        CompanyWithStats amd = new CompanyWithStats();
        amd.setId(2L);
        amd.setTicker("AMD");
        when(companyService.getAllWithStats()).thenReturn(List.of(company, amd));

        Periods nvdaPeriods = periodsWithLatest("25Q2");
        when(periodService.getBy(1L)).thenReturn(nvdaPeriods);
        when(targetDao.listByPeriodIds(List.of(10L))).thenReturn(List.of());
        when(estimateService.getLatest(10L, Estimate.EPS))
                .thenReturn(Optional.of(estimates(LocalDateTime.now().minusDays(1))));

        Periods amdPeriods = periodsWithLatest("25Q2");
        amdPeriods.getPeriods().getFirst().setId(20L);
        when(periodService.getBy(2L)).thenReturn(amdPeriods);
        when(targetDao.listByPeriodIds(List.of(20L))).thenReturn(List.of());
        PeriodEstimates higherEstimates = estimates(LocalDateTime.now().minusDays(1));
        higherEstimates.setNext3(new BigDecimal("20"));
        when(estimateService.getLatest(20L, Estimate.EPS)).thenReturn(Optional.of(higherEstimates));

        OutperformersDto result = outperformersService.get();

        assertThat(result.getEpsEstimates().stream().map(EpsOutperformerDto::getTicker).toList(),
                contains("AMD", "NVDA"));
    }

    private Periods periodsWithLatest(String periodName)
    {
        Periods.Period period = new Periods.Period();
        period.setId(10L);
        period.setName(PeriodName.valueOf(periodName));
        period.setEndingMonth(YearMonth.of(2025, 7));
        Periods periods = new Periods();
        periods.getPeriods().add(period);
        return periods;
    }

    private Periods periodWithSingleFinancial(String periodName, LocalDate reportDate)
    {
        Periods.Period period = new Periods.Period();
        period.setId(10L);
        period.setName(PeriodName.valueOf(periodName));
        period.setEndingMonth(YearMonth.of(2025, 7));
        period.setReportDate(Date.valueOf(reportDate));
        period.setFinancial(new Periods.Financial());

        Periods periods = new Periods();
        periods.getPeriods().add(period);
        periods.setTtm(ttmFinancial());
        return periods;
    }

    private Periods periodsWithConsecutiveFinancials(String latestPeriodName, LocalDate latestReportDate)
    {
        Periods periods = new Periods();
        PeriodName name = PeriodName.valueOf(latestPeriodName);
        long id = 10L;
        for (int i = 0; i < 4; i++) {
            Periods.Period period = new Periods.Period();
            period.setId(id++);
            period.setName(name);
            period.setEndingMonth(YearMonth.of(2025, 7));
            period.setReportDate(Date.valueOf(latestReportDate.minusMonths(3L * i)));
            period.setFinancial(new Periods.Financial());
            periods.getPeriods().add(period);
            name = previousQuarter(name);
        }
        periods.setTtm(ttmFinancial());
        return periods;
    }

    private PeriodName previousQuarter(PeriodName name)
    {
        int year = name.getYear().getValue();
        int number = name.getType().getNumber();
        return number == 1
                ? PeriodName.valueOf(String.format("%02dQ4", (year - 1) % 100))
                : PeriodName.valueOf(String.format("%02dQ%d", year % 100, number - 1));
    }

    private Periods.Financial ttmFinancial()
    {
        Periods.Financial ttm = new Periods.Financial();
        ttm.getRevenue().setValue(new BigDecimal("1000"));
        ttm.getGrossProfit().setValue(new BigDecimal("600"));
        ttm.getGrossProfit().setMargin(new BigDecimal("60.0"));
        ttm.getOperatingIncome().setValue(new BigDecimal("300"));
        ttm.getOperatingIncome().setMargin(new BigDecimal("30.0"));
        ttm.getNetIncome().setValue(new BigDecimal("200"));
        ttm.getNetIncome().setMargin(new BigDecimal("20.0"));
        return ttm;
    }

    private PeriodEstimates estimates(LocalDateTime datetime)
    {
        PeriodEstimates estimates = new PeriodEstimates();
        estimates.setDatetime(datetime);
        estimates.setPast4(new BigDecimal("4"));
        estimates.setPast3(new BigDecimal("4"));
        estimates.setPast2(new BigDecimal("4"));
        estimates.setPast1(new BigDecimal("4"));
        estimates.setCurrent(new BigDecimal("5"));
        estimates.setNext1(new BigDecimal("6"));
        estimates.setNext2(new BigDecimal("7"));
        estimates.setNext3(new BigDecimal("8"));
        return estimates;
    }

    private FirebaseCompany.NewsSentiment sentiment(Map<String, Integer> counts)
    {
        FirebaseCompany.NewsSentiment sentiment = new FirebaseCompany.NewsSentiment();
        sentiment.setSentiment(counts);
        return sentiment;
    }

    private Target target(LocalDate date, String price)
    {
        Target target = new Target();
        target.setDate(Date.valueOf(date));
        target.setPrice(new BigDecimal(price));
        return target;
    }
}
