package org.kaleta.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.model.Periods;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.persistence.entity.PeriodType;
import org.kaleta.rest.dto.PeriodCreateDto;
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.dto.PeriodUnreportedImportDto;
import org.kaleta.rest.dto.PeriodUpdateDto;
import org.kaleta.rest.dto.PeriodUpdateFinancialDto;
import org.kaleta.rest.error.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class PeriodService
{
    @Inject
    PeriodDao periodDao;
    @Inject
    CompanyService companyService;
    @Inject
    FirebaseService firebaseService;
    @Inject
    ArithmeticService arithmeticService;

    public void create(PeriodCreateDto dto)
    {
        Period period = new Period();
        period.setCompany(companyService.findEntity(dto.getCompanyId()));
        period.setName(PeriodName.valueOf(dto.getName()));
        period.setEndingMonth(YearMonth.parse(dto.getEndingMonth()));
        period.setReportDate(Utils.nullableDateValueOf(dto.getReportDate()));
        periodDao.create(period);
    }

    public void create(PeriodImportDto dto)
    {
        Period period = new Period();
        period.setCompany(companyService.findEntity(dto.getCompanyId()));
        period.setName(PeriodName.valueOf(dto.getName()));
        period.setEndingMonth(YearMonth.parse(dto.getEndingMonth()));
        period.setReportDate(Utils.nullableDateValueOf(dto.getReportDate()));
        period.setShares(Utils.createNullableBigDecimal(dto.getShares()));
        period.setPriceLow(Utils.createNullableBigDecimal(dto.getPriceLow()));
        period.setPriceHigh(Utils.createNullableBigDecimal(dto.getPriceHigh()));
        period.setRevenue(Utils.createNullableBigDecimal(dto.getRevenue()));
        period.setGrossProfit(Utils.createNullableBigDecimal(dto.getGrossProfit()));
        period.setOperatingIncome(Utils.createNullableBigDecimal(dto.getOperatingIncome()));
        period.setNetIncome(Utils.createNullableBigDecimal(dto.getNetIncome()));
        period.setDividend(Utils.createNullableBigDecimal(dto.getDividend()));

        periodDao.create(period);
        pushFirebase(period);
    }

    public void create(PeriodUnreportedImportDto dto)
    {
        Period period = new Period();
        period.setCompany(companyService.findEntity(dto.getCompanyId()));
        period.setName(PeriodName.valueOf(dto.getName()));
        period.setEndingMonth(YearMonth.parse(dto.getEndingMonth()));
        periodDao.create(period);
    }

    public void update(PeriodUpdateDto dto)
    {
        Period period;
        try {
            period = periodDao.get(dto.getId());
        } catch (NoResultException e){
            throw new InvalidInputException("period with id '" + dto.getId() + "' not found");
        }

        if (dto.getName() != null) period.setName(PeriodName.valueOf(dto.getName()));
        if (dto.getEndingMonth() != null) period.setEndingMonth(YearMonth.parse(dto.getEndingMonth()));
        if (dto.getReportDate() != null) period.setReportDate(Utils.nullableDateValueOf(dto.getReportDate()));
        if (dto.getShares() != null) period.setShares(new BigDecimal(dto.getShares()));
        if (dto.getPriceHigh() != null) period.setPriceHigh(new BigDecimal(dto.getPriceHigh()));
        if (dto.getPriceLow() != null) period.setPriceLow(new BigDecimal(dto.getPriceLow()));
        if (dto.getResearch() != null) period.setResearch(dto.getResearch());
        if (dto.getRevenue() != null) period.setRevenue(new BigDecimal(dto.getRevenue()));
        if (dto.getGrossProfit() != null) period.setGrossProfit(new BigDecimal(dto.getGrossProfit()));
        if (dto.getOperatingIncome() != null) period.setOperatingIncome(new BigDecimal(dto.getOperatingIncome()));
        if (dto.getNetIncome() != null) period.setNetIncome(new BigDecimal(dto.getNetIncome()));
        if (dto.getDividend() != null) period.setDividend(new BigDecimal(dto.getDividend()));
        if (dto.getAdjustedEps() != null) period.setAdjustedEps(new BigDecimal(dto.getAdjustedEps()));

        periodDao.save(period);
    }


    public void update(PeriodUpdateFinancialDto dto)
    {
        Period period;
        try {
            period = periodDao.get(dto.getId());
        } catch (NoResultException e){
            throw new InvalidInputException("period with id '" + dto.getId() + "' not found");
        }

        period.setReportDate(Utils.nullableDateValueOf(dto.getReportDate()));
        period.setShares(new BigDecimal(dto.getShares()));
        period.setPriceHigh(new BigDecimal(dto.getPriceHigh()));
        period.setPriceLow(new BigDecimal(dto.getPriceLow()));
        period.setRevenue(new BigDecimal(dto.getRevenue()));
        period.setGrossProfit(new BigDecimal(dto.getGrossProfit()));
        period.setOperatingIncome(new BigDecimal(dto.getOperatingIncome()));
        period.setNetIncome(new BigDecimal(dto.getNetIncome()));
        period.setDividend(new BigDecimal(dto.getDividend()));
        period.setAdjustedEps(new BigDecimal(dto.getAdjustedEps()));

        periodDao.save(period);
        pushFirebase(period);
    }

    public Periods getBy(Long companyId)
    {
        List<Period> periods = periodDao.list(companyId);
        periods.sort((a, b) -> -a.getEndingMonth().compareTo(b.getEndingMonth()));

        Periods model = new Periods();

        for (Period period : periods) {
            Periods.Period periodModel = from(period);
            if (period.getRevenue() != null){
                Periods.Financial financial = computeFinancial(period);
                model.getFinancials().add(financial);
                periodModel.setFinancial(financial);
            }
            model.getPeriods().add(periodModel);
        }

        computeFinancialGrowth(model.getFinancials());

        Period ttm = computeTtm(
                periods.stream()
                        .filter(p -> p.getRevenue() != null)
                        .limit(4)
                        .collect(Collectors.toList()));
        if (ttm != null){
            model.setTtm(computeFinancial(ttm));
        }

        for (int i = 0; i < model.getPeriods().size() - 1; i++) {
            Periods.Period current = model.getPeriods().get(i);
            Periods.Period previous = model.getPeriods().get(i + 1);
            if (previous != null && previous.getReportDate() != null) {
                current.setPreviousReportDate(previous.getReportDate());
            }
        }

        return model;
    }

    public Period get(Long id) {
        try {
            return periodDao.get(id);
        } catch (NoResultException exception) {
            throw new InvalidInputException("period with id '" + id + "' not found");
        }
    }

    private void pushFirebase(Period period){
        try {
            firebaseService.updatePeriod(period);
        } catch (RuntimeException exception) {
            Log.error(exception.getMessage(), exception);
        }
    }

    private Periods.Period from(Period entity)
    {
        Periods.Period period = new Periods.Period();
        period.setId(entity.getId());
        period.setName(entity.getName());
        period.setEndingMonth(entity.getEndingMonth());
        period.setReportDate(entity.getReportDate());
        period.setShares(entity.getShares());
        period.setPriceLow(entity.getPriceLow());
        period.setPriceHigh(entity.getPriceHigh());
        period.setResearch(entity.getResearch());
        return period;
    }

    private Periods.Financial computeFinancial(Period period)
    {
        Periods.Financial financial = new Periods.Financial();

        financial.setPeriod(period.getName());
        financial.getRevenue().setValue(period.getRevenue());
        financial.getGrossProfit().setValue(period.getGrossProfit());
        financial.getOperatingIncome().setValue(period.getOperatingIncome());
        financial.getNetIncome().setValue(period.getNetIncome());

        financial.getRevenue().setMargin(new BigDecimal(100));

        BigDecimal grossMargin = period.getGrossProfit().multiply(new BigDecimal(100)).divide(period.getRevenue(), 2, RoundingMode.HALF_UP);
        financial.getGrossProfit().setMargin(grossMargin);

        BigDecimal operatingMargin = period.getOperatingIncome().multiply(new BigDecimal(100)).divide(period.getRevenue(), 2, RoundingMode.HALF_UP);
        financial.getOperatingIncome().setMargin(operatingMargin);

        BigDecimal netMargin = period.getNetIncome().multiply(new BigDecimal(100)).divide(period.getRevenue(), 2, RoundingMode.HALF_UP);
        financial.getNetIncome().setMargin(netMargin);

        financial.setDividend(period.getDividend());
        financial.setAdjustedEps(period.getAdjustedEps());

        financial.setShares(period.getShares());

        return financial;
    }

    private void computeFinancialGrowth(List<Periods.Financial> financials)
    {
        if (financials.isEmpty()) {
            return;
        }

        if (allOfType(financials, PeriodType.Q1, PeriodType.Q2, PeriodType.Q3, PeriodType.Q4)) {
            computeFinancialGrowth(financials, 1, 4);
            return;
        }

        if (allOfType(financials, PeriodType.H1, PeriodType.H2)) {
            computeFinancialGrowth(financials, null, 2);
            return;
        }

        if (allOfType(financials, PeriodType.FY)) {
            computeFinancialGrowth(financials, null, 1);
        }
    }

    private void computeFinancialGrowth(List<Periods.Financial> financials, Integer qoqOffset, int yoyOffset)
    {
        for (int i = 0; i < financials.size(); i++) {
            Periods.Financial current = financials.get(i);
            Periods.Financial previousPeriod = qoqOffset == null || financials.size() <= i + qoqOffset ? null : financials.get(i + qoqOffset);
            Periods.Financial previousYear = financials.size() <= i + yoyOffset ? null : financials.get(i + yoyOffset);

            computeMetricGrowth(
                    current.getRevenue(),
                    previousPeriod == null ? null : previousPeriod.getRevenue(),
                    previousYear == null ? null : previousYear.getRevenue());
            computeMetricGrowth(
                    current.getGrossProfit(),
                    previousPeriod == null ? null : previousPeriod.getGrossProfit(),
                    previousYear == null ? null : previousYear.getGrossProfit());
            computeMetricGrowth(
                    current.getOperatingIncome(),
                    previousPeriod == null ? null : previousPeriod.getOperatingIncome(),
                    previousYear == null ? null : previousYear.getOperatingIncome());
            computeMetricGrowth(
                    current.getNetIncome(),
                    previousPeriod == null ? null : previousPeriod.getNetIncome(),
                    previousYear == null ? null : previousYear.getNetIncome());
        }
    }

    private boolean allOfType(List<Periods.Financial> financials, PeriodType... types)
    {
        List<PeriodType> expectedTypes = List.of(types);
        return financials.stream()
                .map(financial -> financial.getPeriod().getType())
                .allMatch(expectedTypes::contains);
    }

    private void computeMetricGrowth(Periods.Financial.Metric current,
                                     Periods.Financial.Metric previousQuarter,
                                     Periods.Financial.Metric previousYear)
    {
        if (current.getValue() == null) {
            return;
        }
        if (previousQuarter != null && canComputeGrowth(current.getValue(), previousQuarter.getValue())) {
            current.setQoq(arithmeticService.profitPercentage(previousQuarter.getValue(), current.getValue()));
        }
        if (previousYear != null && canComputeGrowth(current.getValue(), previousYear.getValue())) {
            current.setYoy(arithmeticService.profitPercentage(previousYear.getValue(), current.getValue()));
        }
    }

    private boolean canComputeGrowth(BigDecimal current, BigDecimal previous)
    {
        return previous != null
                && current.compareTo(BigDecimal.ZERO) >= 0
                && previous.compareTo(BigDecimal.ZERO) >= 0;
    }

    private Period computeTtm(List<Period> periods)
    {
        if (periods.isEmpty()) return null;
        periods.sort((a, b) -> -a.getEndingMonth().compareTo(b.getEndingMonth()));
        List<Period> quarters = periodsToQuarters(periods);

        BigDecimal revenue = new BigDecimal(0);
        BigDecimal cogs = new BigDecimal(0);
        BigDecimal opExpenses = new BigDecimal(0);
        BigDecimal netIncome = new BigDecimal(0);
        BigDecimal dividend = new BigDecimal(0);

        for (int i=0; i<4; i++){
            if (quarters.size() > i){
                revenue = revenue.add(quarters.get(i).getRevenue());
                cogs = cogs.add(quarters.get(i).getGrossProfit());
                opExpenses = opExpenses.add(quarters.get(i).getOperatingIncome());
                netIncome = netIncome.add(quarters.get(i).getNetIncome());
                dividend = dividend.add(quarters.get(i).getDividend());
            } else {
                BigDecimal multiplier = new BigDecimal(4).divide(new BigDecimal(i), 4, RoundingMode.HALF_UP);
                revenue = revenue.multiply(multiplier);
                cogs = cogs.multiply(multiplier);
                opExpenses = opExpenses.multiply(multiplier);
                netIncome = netIncome.multiply(multiplier);
                dividend = dividend.multiply(multiplier);
                break;
            }
        }

        Period ttm = new Period();
        ttm.setRevenue(revenue.setScale(0, RoundingMode.HALF_UP));
        ttm.setGrossProfit(cogs.setScale(0, RoundingMode.HALF_UP));
        ttm.setOperatingIncome(opExpenses.setScale(0, RoundingMode.HALF_UP));
        ttm.setNetIncome(netIncome.setScale(0, RoundingMode.HALF_UP));
        ttm.setDividend(dividend.setScale(0, RoundingMode.HALF_UP));
        ttm.setShares(quarters.get(0).getShares());
        return ttm;
    }

    private List<Period> periodsToQuarters(List<Period> periods)
    {
        List<Period> quarters = new ArrayList<>();
        for (Period period : periods){
            if (period.getRevenue() == null)
                throw new IllegalArgumentException("not reported period provided for ttm computation!");
            Period quarter = new Period();
            switch (period.getName().getType()){
                case FY:
                    quarter.setRevenue(period.getRevenue().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setGrossProfit(period.getGrossProfit().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setOperatingIncome(period.getOperatingIncome().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setNetIncome(period.getNetIncome().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setDividend(period.getDividend().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setShares(period.getShares());
                    quarters.add(quarter);
                    quarters.add(quarter);
                    quarters.add(quarter);
                    quarters.add(quarter);
                    break;
                case H1: case H2:
                    quarter.setRevenue(period.getRevenue().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setGrossProfit(period.getGrossProfit().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setOperatingIncome(period.getOperatingIncome().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setNetIncome(period.getNetIncome().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setDividend(period.getDividend().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setShares(period.getShares());
                    quarters.add(quarter);
                    quarters.add(quarter);
                    break;
                case Q1: case Q2: case Q3: case Q4:
                    quarters.add(period);
                    break;
                default: throw new IllegalArgumentException("Invalid period type: '" + period.getName().getType() + "'");
            }
        }
        return quarters;
    }
}
