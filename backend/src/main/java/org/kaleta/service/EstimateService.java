package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.Utils;
import org.kaleta.model.EstimateOverview;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.persistence.api.EstimateDao;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodType;
import org.kaleta.rest.dto.EstimateCreateDto;
import org.kaleta.rest.dto.EstimateDto;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    public Optional<PeriodEstimates> getLatest(Long periodId, boolean type)
    {
        periodService.get(periodId);
        return estimateDao.findLatest(periodId, type)
                .map(this::from);
    }

    public Map<Long, PeriodEstimates> getLatestByPeriodIds(List<Long> periodIds, boolean type)
    {
        Map<Long, Map<String, BigDecimal>> pastValuesByCompany = new java.util.HashMap<>();
        return estimateDao.findLatestByPeriodIds(periodIds, type).stream()
                .collect(Collectors.toMap(
                        estimate -> estimate.getPeriod().getId(),
                        estimate -> from(estimate, pastValuesByCompany.computeIfAbsent(
                                estimate.getPeriod().getCompany().getId(),
                                companyId -> pastValuesByQuarter(companyId, type)))));
    }

    public List<EstimateDto> getAll(Long periodId, boolean type)
    {
        periodService.get(periodId);
        return estimateDao.list(periodId, type).stream()
                .map(this::toDto)
                .toList();
    }

    public EstimateOverview createOverview(PeriodEstimates estimates)
    {
        List<BigDecimal> values = java.util.Arrays.asList(
                estimates.getPast4(), estimates.getPast3(), estimates.getPast2(), estimates.getPast1(),
                estimates.getCurrent(), estimates.getNext1(), estimates.getNext2(), estimates.getNext3());
        List<BigDecimal> totals = java.util.stream.IntStream.rangeClosed(0, 4)
                .mapToObj(offset -> sum(values.subList(offset, offset + 4)))
                .toList();

        EstimateOverview overview = new EstimateOverview();
        setOverviewWindow(overview.getTtm(), totals.get(0), null);
        setOverviewWindow(overview.getCurrent(), totals.get(1), estimates.getCurrentChange());
        setOverviewWindow(overview.getNext1(), totals.get(2), estimates.getNext1Change());
        setOverviewWindow(overview.getNext2(), totals.get(3), estimates.getNext2Change());
        setOverviewWindow(overview.getNext3(), totals.get(4), estimates.getNext3Change());
        if (totals.get(0) != null && totals.get(4) != null) {
            overview.setYearOverYearChange(arithmeticService.profitPercentage(totals.get(0), totals.get(4)));
        }
        return overview;
    }

    public void create(Long periodId, boolean type, EstimateCreateDto dto)
    {
        Estimate estimate = new Estimate();
        estimate.setPeriod(periodService.get(periodId));
        estimate.setType(type);
        estimate.setDatetime(LocalDate.parse(dto.getDate()).atStartOfDay());
        estimate.setCurrent(new BigDecimal(dto.getCurrent()));
        estimate.setNext1(Utils.createNullableBigDecimal(dto.getNext1()));
        estimate.setNext2(Utils.createNullableBigDecimal(dto.getNext2()));
        estimate.setNext3(Utils.createNullableBigDecimal(dto.getNext3()));
        estimateDao.create(estimate);
    }

    private EstimateDto toDto(Estimate estimate)
    {
        EstimateDto dto = new EstimateDto();
        dto.setId(estimate.getId());
        dto.setPeriodId(estimate.getPeriod().getId());
        dto.setDatetime(estimate.getDatetime());
        dto.setType(estimate.isType());
        dto.setCurrent(estimate.getCurrent());
        dto.setNext1(estimate.getNext1());
        dto.setNext2(estimate.getNext2());
        dto.setNext3(estimate.getNext3());
        return dto;
    }

    private PeriodEstimates from(Estimate estimate)
    {
        return from(
                estimate,
                pastValuesByQuarter(estimate.getPeriod().getCompany().getId(), estimate.isType()));
    }

    private PeriodEstimates from(Estimate estimate, Map<String, BigDecimal> pastValuesByQuarter)
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
            dto.setPast1(previousValue(pastValuesByQuarter, quarter, 1));
            dto.setPast2(previousValue(pastValuesByQuarter, quarter, 2));
            dto.setPast3(previousValue(pastValuesByQuarter, quarter, 3));
            dto.setPast4(previousValue(pastValuesByQuarter, quarter, 4));
            setRollingChanges(dto);
        }
        return dto;
    }

    private void setRollingChanges(PeriodEstimates estimates)
    {
        List<BigDecimal> values = java.util.Arrays.asList(
                estimates.getPast4(), estimates.getPast3(), estimates.getPast2(), estimates.getPast1(),
                estimates.getCurrent(), estimates.getNext1(), estimates.getNext2(), estimates.getNext3());
        estimates.setPastTotal(sum(values.subList(0, 4)));
        estimates.setCurrentChange(rollingFourQuarterChange(values, 0));
        estimates.setNext1Change(rollingFourQuarterChange(values, 1));
        estimates.setNext2Change(rollingFourQuarterChange(values, 2));
        estimates.setNext3Change(rollingFourQuarterChange(values, 3));
    }

    private void setOverviewWindow(
            EstimateOverview.Window window,
            BigDecimal value,
            BigDecimal change)
    {
        window.setValue(value);
        window.setChange(change);
    }

    private BigDecimal rollingFourQuarterChange(List<BigDecimal> values, int offset)
    {
        List<BigDecimal> previousWindow = values.subList(offset, offset + 4);
        List<BigDecimal> nextWindow = values.subList(offset + 1, offset + 5);
        if (previousWindow.contains(null) || nextWindow.contains(null)) {
            return null;
        }

        BigDecimal previousTotal = sum(previousWindow);
        BigDecimal nextTotal = sum(nextWindow);
        return arithmeticService.profitPercentage(previousTotal, nextTotal);
    }

    private BigDecimal sum(List<BigDecimal> values)
    {
        return values.contains(null) ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> pastValuesByQuarter(Long companyId, boolean type)
    {
        Map<String, BigDecimal> pastValuesByQuarter = new java.util.HashMap<>();
        for (Period period : periodDao.list(companyId)) {
            pastValuesByQuarter.putIfAbsent(
                    period.getName().toString(),
                    type == Estimate.EPS ? period.getAdjustedEps() : period.getRevenue());
        }
        return pastValuesByQuarter;
    }

    private BigDecimal previousValue(
            Map<String, BigDecimal> pastValuesByQuarter,
            String quarter,
            int offset)
    {
        return pastValuesByQuarter.get(arithmeticService.shiftQuarter(quarter, -offset));
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
