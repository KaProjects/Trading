package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.Utils;
import org.kaleta.persistence.api.EstimateDao;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.rest.dto.EstimateCreateDto;
import org.kaleta.rest.dto.EstimateDto;

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

    public Optional<EstimateDto> getLatest(Long periodId)
    {
        periodService.get(periodId);
        return estimateDao.findLatest(periodId)
                .map(this::from);
    }

    public Map<Long, EstimateDto> getLatestByPeriodIds(List<Long> periodIds)
    {
        return estimateDao.findLatestByPeriodIds(periodIds).stream()
                .collect(Collectors.toMap(
                        estimate -> estimate.getPeriod().getId(),
                        this::from));
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

    private EstimateDto from(Estimate estimate)
    {
        EstimateDto dto = new EstimateDto();
        dto.setId(estimate.getId());
        dto.setPeriodId(estimate.getPeriod().getId());
        dto.setDatetime(estimate.getDatetime());
        dto.setCurrent(estimate.getCurrent());
        dto.setNext1(estimate.getNext1());
        dto.setNext2(estimate.getNext2());
        dto.setNext3(estimate.getNext3());
        return dto;
    }
}
