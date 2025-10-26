package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.model.Periods;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.PeriodCreateDto;
import org.kaleta.rest.dto.PeriodUpdateDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class PeriodService
{
    @Inject
    PeriodDao periodDao;
    @Inject
    CompanyService companyService;

    public void create(PeriodCreateDto dto)
    {
        Period period = new Period();
        period.setCompany(companyService.getCompany(dto.getCompanyId()));
        period.setName(PeriodName.valueOf(dto.getName()));
        period.setEndingMonth(YearMonth.parse(dto.getEndingMonth()));
        period.setReportDate(Utils.nullableDateValueOf(dto.getReportDate()));
        periodDao.create(period);
    }

    public void update(PeriodUpdateDto dto)
    {
        Period period;
        try {
            period = periodDao.get(dto.getId());
        } catch (NoResultException e){
            throw new ServiceFailureException("period with id '" + dto.getId() + "' not found");
        }

        if (dto.getName() != null) period.setName(PeriodName.valueOf(dto.getName()));
        if (dto.getEndingMonth() != null) period.setEndingMonth(YearMonth.parse(dto.getEndingMonth()));
        if (dto.getReportDate() != null) period.setReportDate(Utils.nullableDateValueOf(dto.getReportDate()));
        if (dto.getShares() != null) period.setShares(new BigDecimal(dto.getShares()));
        if (dto.getPriceHigh() != null) period.setPriceHigh(new BigDecimal(dto.getPriceHigh()));
        if (dto.getPriceLow() != null) period.setPriceLow(new BigDecimal(dto.getPriceLow()));
        if (dto.getResearch() != null) period.setResearch(dto.getResearch());
        if (dto.getRevenue() != null) period.setRevenue(new BigDecimal(dto.getRevenue()));
        if (dto.getCostGoodsSold() != null) period.setCostGoodsSold(new BigDecimal(dto.getCostGoodsSold()));
        if (dto.getOperatingExpenses() != null) period.setOperatingExpenses(new BigDecimal(dto.getOperatingExpenses()));
        if (dto.getNetIncome() != null) period.setNetIncome(new BigDecimal(dto.getNetIncome()));
        if (dto.getDividend() != null) period.setDividend(new BigDecimal(dto.getDividend()));

        periodDao.save(period);
    }

    public Periods getBy(String companyId)
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
        Period ttm = computeTtm(
                periods.stream()
                        .filter(p -> p.getRevenue() != null)
                        .limit(4)
                        .collect(Collectors.toList()));
        if (ttm != null){
            model.setTtm(computeFinancial(ttm));
        }
        return model;
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
        financial.setRevenue(period.getRevenue());
        financial.setCostGoodsSold(period.getCostGoodsSold());
        financial.setOperatingExpenses(period.getOperatingExpenses());
        financial.setNetIncome(period.getNetIncome());

        BigDecimal grossProfit = period.getRevenue().subtract(period.getCostGoodsSold());
        financial.setGrossProfit(grossProfit);

        BigDecimal grossMargin = grossProfit.multiply(new BigDecimal(100)).divide(period.getRevenue(), 2, RoundingMode.HALF_UP);
        financial.setGrossMargin(grossMargin);

        BigDecimal operatingIncome = grossProfit.subtract(period.getOperatingExpenses());
        financial.setOperatingIncome(operatingIncome);

        BigDecimal operatingMargin = operatingIncome.multiply(new BigDecimal(100)).divide(period.getRevenue(), 2, RoundingMode.HALF_UP);
        financial.setOperatingMargin(operatingMargin);

        BigDecimal netMargin = period.getNetIncome().multiply(new BigDecimal(100)).divide(period.getRevenue(), 2, RoundingMode.HALF_UP);
        financial.setNetMargin(netMargin);

        financial.setDividend(period.getDividend());

        return financial;
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
                cogs = cogs.add(quarters.get(i).getCostGoodsSold());
                opExpenses = opExpenses.add(quarters.get(i).getOperatingExpenses());
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
        ttm.setCostGoodsSold(cogs.setScale(0, RoundingMode.HALF_UP));
        ttm.setOperatingExpenses(opExpenses.setScale(0, RoundingMode.HALF_UP));
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
                throw new ServiceFailureException("not reported period provided for ttm computation!");
            Period quarter = new Period();
            switch (period.getName().getType()){
                case FY:
                    quarter.setRevenue(period.getRevenue().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setCostGoodsSold(period.getCostGoodsSold().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setOperatingExpenses(period.getOperatingExpenses().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
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
                    quarter.setCostGoodsSold(period.getCostGoodsSold().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setOperatingExpenses(period.getOperatingExpenses().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setNetIncome(period.getNetIncome().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setDividend(period.getDividend().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setShares(period.getShares());
                    quarters.add(quarter);
                    quarters.add(quarter);
                    break;
                case Q1: case Q2: case Q3: case Q4:
                    quarters.add(period);
                    break;
                default: throw new ServiceFailureException("Invalid period type: '" + period.getName().getType() + "'");
            }
        }
        return quarters;
    }
}
