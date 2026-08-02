package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Generator;
import org.kaleta.persistence.api.EstimateDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.persistence.entity.Period;
import org.kaleta.rest.dto.EstimateCreateDto;
import org.kaleta.rest.dto.EstimateDto;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Inject
    EstimateService estimateService;

    private Period period;

    @BeforeEach
    void before()
    {
        reset(estimateDao, periodService);
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

        EstimateDto dto = estimateService.getLatest(period.getId()).orElseThrow();

        assertThat(dto.getId(), is(estimate.getId()));
        assertThat(dto.getPeriodId(), is(period.getId()));
        assertThat(dto.getDatetime(), is(estimate.getDatetime()));
        assertBigDecimals(dto.getCurrent(), estimate.getCurrent());
        assertBigDecimals(dto.getNext1(), estimate.getNext1());
        assertBigDecimals(dto.getNext2(), estimate.getNext2());
        assertThat(dto.getNext3(), is(nullValue()));
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

        Map<Long, EstimateDto> estimates = estimateService.getLatestByPeriodIds(periodIds);

        assertThat(estimates.size(), is(1));
        assertThat(estimates.get(period.getId()).getId(), is(estimate.getId()));
    }

    @Test
    void create()
    {
        EstimateCreateDto dto = createDto();
        LocalDateTime before = LocalDateTime.now();

        estimateService.create(period.getId(), dto);

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<Estimate> captor = ArgumentCaptor.forClass(Estimate.class);
        verify(estimateDao).create(captor.capture());
        Estimate estimate = captor.getValue();
        assertThat(estimate.getPeriod(), is(period));
        assertThat(estimate.getDatetime(), is(notNullValue()));
        assertFalse(estimate.getDatetime().isBefore(before));
        assertFalse(estimate.getDatetime().isAfter(after));
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
        dto.setCurrent("11.50");
        dto.setNext1("12.75");
        dto.setNext3("14.25");
        return dto;
    }
}
