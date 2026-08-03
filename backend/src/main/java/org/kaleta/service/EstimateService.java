package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.Utils;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.persistence.api.EstimateDao;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodType;
import org.kaleta.rest.dto.EstimateCreateDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class EstimateService
{
    @Inject
    EstimateDao estimateDao;
    @Inject
    PeriodService periodService;
    @Inject
    PeriodDao periodDao;
    @Inject
    ArithmeticService arithmeticService;

    public Optional<PeriodEstimates> getLatest(Long periodId)
    {
        periodService.get(periodId);
        return estimateDao.findLatest(periodId)
                .map(this::from);
    }

    public Map<Long, PeriodEstimates> getLatestByPeriodIds(List<Long> periodIds)
    {
        Map<Long, Map<String, BigDecimal>> adjustedEpsByCompany = new java.util.HashMap<>();
        return estimateDao.findLatestByPeriodIds(periodIds).stream()
                .collect(Collectors.toMap(
                        estimate -> estimate.getPeriod().getId(),
                        estimate -> from(estimate, adjustedEpsByCompany.computeIfAbsent(
                                estimate.getPeriod().getCompany().getId(),
                                this::adjustedEpsByQuarter))));
    }

    public void create(Long periodId, EstimateCreateDto dto)
    {
        Estimate estimate = new Estimate();
        estimate.setPeriod(periodService.get(periodId));
        estimate.setDatetime(LocalDateTime.now());
        estimate.setCurrent(new BigDecimal(dto.getCurrent()));
        estimate.setNext1(Utils.createNullableBigDecimal(dto.getNext1()));
        estimate.setNext2(Utils.createNullableBigDecimal(dto.getNext2()));
        estimate.setNext3(Utils.createNullableBigDecimal(dto.getNext3()));
        estimateDao.create(estimate);
    }

    private PeriodEstimates from(Estimate estimate)
    {
        return from(estimate, adjustedEpsByQuarter(estimate.getPeriod().getCompany().getId()));
    }

    private PeriodEstimates from(Estimate estimate, Map<String, BigDecimal> adjustedEpsByQuarter)
    {
        PeriodEstimates dto = new PeriodEstimates();
        dto.setId(estimate.getId());
        dto.setPeriodId(estimate.getPeriod().getId());
        dto.setDatetime(estimate.getDatetime());
        dto.setCurrent(estimate.getCurrent());
        dto.setNext1(estimate.getNext1());
        dto.setNext2(estimate.getNext2());
        dto.setNext3(estimate.getNext3());
        if (isQuarter(estimate.getPeriod())) {
            String quarter = estimate.getPeriod().getName().toString();
            dto.setPast1(previousAdjustedEps(adjustedEpsByQuarter, quarter, 1));
            dto.setPast2(previousAdjustedEps(adjustedEpsByQuarter, quarter, 2));
            dto.setPast3(previousAdjustedEps(adjustedEpsByQuarter, quarter, 3));
            dto.setPast4(previousAdjustedEps(adjustedEpsByQuarter, quarter, 4));
        }
        return dto;
    }

    private Map<String, BigDecimal> adjustedEpsByQuarter(Long companyId)
    {
        Map<String, BigDecimal> adjustedEpsByQuarter = new java.util.HashMap<>();
        for (Period period : periodDao.list(companyId)) {
            adjustedEpsByQuarter.putIfAbsent(period.getName().toString(), period.getAdjustedEps());
        }
        return adjustedEpsByQuarter;
    }

    private BigDecimal previousAdjustedEps(
            Map<String, BigDecimal> adjustedEpsByQuarter,
            String quarter,
            int offset)
    {
        return adjustedEpsByQuarter.get(arithmeticService.shiftQuarter(quarter, -offset));
    }

    private boolean isQuarter(Period period)
    {
        PeriodType type = period.getName().getType();
        return type == PeriodType.Q1
                || type == PeriodType.Q2
                || type == PeriodType.Q3
                || type == PeriodType.Q4;
    }
}
