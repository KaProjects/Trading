package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Generator;
import org.kaleta.model.EstimateOverview;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.persistence.api.EstimateDao;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.persistence.entity.Period;
import org.kaleta.rest.dto.EstimateCreateDto;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class EstimateServiceTest
{
    @InjectMock
    EstimateDao estimateDao;
    @InjectMock
    PeriodService periodService;
    @InjectMock
    PeriodDao periodDao;

    @Inject
    EstimateService estimateService;

    private Period period;

    @BeforeEach
    void before()
    {
        reset(estimateDao, periodService, periodDao);
        Company company = Generator.generateCompany(100L);
        period = Generator.generatePeriod(company, false);
        period.setId(200L);
        when(periodService.get(period.getId())).thenReturn(period);
    }

    @Test
    void getLatest()
    {
        Estimate estimate = estimate(300L, "2026-08-02T12:30:00");
        when(estimateDao.findLatest(period.getId())).thenReturn(Optional.of(estimate));

        PeriodEstimates dto = estimateService.getLatest(period.getId()).orElseThrow();

        assertThat(dto.getId(), is(estimate.getId()));
        assertThat(dto.getPeriodId(), is(period.getId()));
        assertThat(dto.getDatetime(), is(estimate.getDatetime()));
        assertBigDecimals(dto.getCurrent(), estimate.getCurrent());
        assertBigDecimals(dto.getNext1(), estimate.getNext1());
        assertBigDecimals(dto.getNext2(), estimate.getNext2());
        assertThat(dto.getNext3(), is(nullValue()));
    }

    @Test
    void getLatest_includesAdjustedEpsFromPreviousQuarters()
    {
        period.setName(org.kaleta.persistence.entity.PeriodName.valueOf("26Q1"));
        Estimate estimate = estimate(300L, "2026-08-02T12:30:00");
        when(estimateDao.findLatest(period.getId())).thenReturn(Optional.of(estimate));
        when(periodDao.list(period.getCompany().getId())).thenReturn(List.of(
                previousPeriod("25Q4", "1.20"),
                previousPeriod("25Q3", "1.10"),
                previousPeriod("25Q2", null),
                previousPeriod("25Q1", "0.90")));

        PeriodEstimates result = estimateService.getLatest(period.getId()).orElseThrow();

        assertBigDecimals(result.getPast1(), new BigDecimal("1.20"));
        assertBigDecimals(result.getPast2(), new BigDecimal("1.10"));
        assertThat(result.getPast3(), is(nullValue()));
        assertBigDecimals(result.getPast4(), new BigDecimal("0.90"));
    }

    @Test
    void getLatest_calculatesRollingFourQuarterChanges()
    {
        period.setName(org.kaleta.persistence.entity.PeriodName.valueOf("26Q1"));
        Estimate estimate = estimate(300L, "2026-08-02T12:30:00");
        estimate.setCurrent(new BigDecimal("5"));
        estimate.setNext1(new BigDecimal("6"));
        estimate.setNext2(new BigDecimal("7"));
        estimate.setNext3(new BigDecimal("8"));
        when(estimateDao.findLatest(period.getId())).thenReturn(Optional.of(estimate));
        when(periodDao.list(period.getCompany().getId())).thenReturn(List.of(
                previousPeriod("25Q4", "4"),
                previousPeriod("25Q3", "3"),
                previousPeriod("25Q2", "2"),
                previousPeriod("25Q1", "1")));

        PeriodEstimates result = estimateService.getLatest(period.getId()).orElseThrow();

        assertBigDecimals(result.getPastTotal(), new BigDecimal("10"));
        assertBigDecimals(result.getCurrentChange(), new BigDecimal("40"));
        assertBigDecimals(result.getNext1Change(), new BigDecimal("28.57"));
        assertBigDecimals(result.getNext2Change(), new BigDecimal("22.22"));
        assertBigDecimals(result.getNext3Change(), new BigDecimal("18.18"));

        EstimateOverview overview = estimateService.createOverview(result);
        assertBigDecimals(overview.getTtm().getValue(), new BigDecimal("10"));
        assertThat(overview.getTtm().getChange(), is(nullValue()));
        assertBigDecimals(overview.getCurrent().getValue(), new BigDecimal("14"));
        assertBigDecimals(overview.getCurrent().getChange(), new BigDecimal("40"));
        assertBigDecimals(overview.getNext1().getValue(), new BigDecimal("18"));
        assertBigDecimals(overview.getNext1().getChange(), new BigDecimal("28.57"));
        assertBigDecimals(overview.getNext2().getValue(), new BigDecimal("22"));
        assertBigDecimals(overview.getNext2().getChange(), new BigDecimal("22.22"));
        assertBigDecimals(overview.getNext3().getValue(), new BigDecimal("26"));
        assertBigDecimals(overview.getNext3().getChange(), new BigDecimal("18.18"));
    }

    @Test
    void getLatest_empty()
    {
        when(estimateDao.findLatest(period.getId())).thenReturn(Optional.empty());

        assertThat(estimateService.getLatest(period.getId()), is(Optional.empty()));
    }

    @Test
    void getLatestByPeriodIds()
    {
        Estimate estimate = estimate(300L, "2026-08-02T12:30:00");
        List<Long> periodIds = List.of(period.getId());
        when(estimateDao.findLatestByPeriodIds(periodIds)).thenReturn(List.of(estimate));

        Map<Long, PeriodEstimates> estimates = estimateService.getLatestByPeriodIds(periodIds);

        assertThat(estimates.size(), is(1));
        assertThat(estimates.get(period.getId()).getId(), is(estimate.getId()));
    }

    @Test
    void getAll()
    {
        Estimate newer = estimate(301L, "2026-08-03T12:30:00");
        Estimate older = estimate(300L, "2026-08-02T12:30:00");
        when(estimateDao.list(period.getId())).thenReturn(List.of(newer, older));

        List<org.kaleta.rest.dto.EstimateDto> result = estimateService.getAll(period.getId());

        assertThat(result.size(), is(2));
        assertThat(result.get(0).getId(), is(301L));
        assertThat(result.get(1).getId(), is(300L));
    }

    @Test
    void create()
    {
        EstimateCreateDto dto = createDto();

        estimateService.create(period.getId(), dto);

        ArgumentCaptor<Estimate> captor = ArgumentCaptor.forClass(Estimate.class);
        verify(estimateDao).create(captor.capture());
        Estimate estimate = captor.getValue();
        assertThat(estimate.getPeriod(), is(period));
        assertThat(estimate.getDatetime(), is(LocalDateTime.parse("2026-08-03T00:00:00")));
        assertBigDecimals(estimate.getCurrent(), new BigDecimal("11.50"));
        assertBigDecimals(estimate.getNext1(), new BigDecimal("12.75"));
        assertThat(estimate.getNext2(), is(nullValue()));
        assertBigDecimals(estimate.getNext3(), new BigDecimal("14.25"));
    }

    @Test
    void create_invalidPeriod()
    {
        Long missingPeriodId = 4_294_967_295L;
        when(periodService.get(missingPeriodId))
                .thenThrow(new InvalidInputException("period not found"));

        assertThrows(
                InvalidInputException.class,
                () -> estimateService.create(missingPeriodId, createDto()));

        verify(estimateDao, never()).create(org.mockito.ArgumentMatchers.any());
    }

    private Estimate estimate(Long id, String datetime)
    {
        Estimate estimate = new Estimate();
        estimate.setId(id);
        estimate.setPeriod(period);
        estimate.setDatetime(LocalDateTime.parse(datetime));
        estimate.setCurrent(new BigDecimal("11.50"));
        estimate.setNext1(new BigDecimal("12.75"));
        estimate.setNext2(new BigDecimal("13.25"));
        return estimate;
    }

    private EstimateCreateDto createDto()
    {
        EstimateCreateDto dto = new EstimateCreateDto();
        dto.setDate("2026-08-03");
        dto.setCurrent("11.50");
        dto.setNext1("12.75");
        dto.setNext3("14.25");
        return dto;
    }

    private Period previousPeriod(String name, String adjustedEps)
    {
        Period previous = Generator.generatePeriod(period.getCompany(), false);
        previous.setName(org.kaleta.persistence.entity.PeriodName.valueOf(name));
        previous.setAdjustedEps(adjustedEps == null ? null : new BigDecimal(adjustedEps));
        return previous;
    }
}
