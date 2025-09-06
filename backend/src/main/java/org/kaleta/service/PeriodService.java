package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.dao.PeriodDao;
import org.kaleta.dto.FinancialDto;
import org.kaleta.dto.PeriodCreateDto;
import org.kaleta.dto.PeriodDto;
import org.kaleta.dto.PeriodsDto;
import org.kaleta.entity.Period;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    @Inject
    ConvertService convertService;

    public void update(PeriodDto dto)
    {
        Period period;
        try {
            period = periodDao.get(dto.getId());
        } catch (NoResultException e){
            throw new ServiceFailureException("period with id '" + dto.getId() + "' not found");
        }

        if (dto.getName() != null) period.setName(dto.getName());
        if (dto.getEndingMonth() != null) period.setEndingMonth(dto.getEndingMonth());
        if (dto.getReportDate() != null) period.setReportDate(convertService.parseDate(dto.getReportDate()));
        if (dto.getShares() != null) period.setShares(new BigDecimal(dto.getShares()));
        if (dto.getPriceLatest() != null) period.setPriceLatest(new BigDecimal(dto.getPriceLatest()));
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

    public void create(PeriodCreateDto dto)
    {
        Period period = new Period();

        period.setCompany(companyService.getCompany(dto.getCompanyId()));
        period.setName(dto.getName());
        period.setEndingMonth(dto.getEndingMonth());
        if (dto.getReportDate() != null) period.setReportDate(convertService.parseDate(dto.getReportDate()));

        periodDao.create(period);
    }

    public PeriodsDto getBy(String companyId)
    {
        PeriodsDto dto = new PeriodsDto();
        dto.setCompany(companyService.getDto(companyId));

        List<Period> periods = periodDao.list(companyId);
        periods.sort((a, b) -> -Utils.compareEndingMonths(a.getEndingMonth(), b.getEndingMonth()));

        for (Period period : periods) {
            dto.getPeriods().add(from(period));
            if (period.getRevenue() != null){
                dto.getFinancials().add(computeFinancialFrom(period));
            }
        }
        dto.setTtm(computeTtmFrom(
                periods.stream()
                        .filter(p -> p.getRevenue() != null)
                        .limit(4)
                        .collect(Collectors.toList()))
        );
        return dto;
    }

    private PeriodDto from(Period period)
    {
        PeriodDto dto = new PeriodDto();
        dto.setId(period.getId());
        dto.setName(period.getName());
        dto.setEndingMonth(convertService.formatMonth(period.getEndingMonth()));
        dto.setReportDate(convertService.format(period.getReportDate()));
        dto.setShares(convertService.formatMillions(period.getShares()));
        dto.setPriceLatest(convertService.format(period.getPriceLatest()));
        dto.setPriceHigh(convertService.format(period.getPriceHigh()));
        dto.setPriceLow(convertService.format(period.getPriceLow()));
        dto.setResearch(period.getResearch());
        dto.setRevenue(convertService.formatMillions(period.getRevenue()));
        dto.setCostGoodsSold(convertService.formatMillions(period.getCostGoodsSold()));
        dto.setOperatingExpenses(convertService.formatMillions(period.getOperatingExpenses()));
        dto.setNetIncome(convertService.formatMillions(period.getNetIncome()));
        dto.setDividend(convertService.formatMillions(period.getDividend()));
        return dto;
    }

    private FinancialDto computeFinancialFrom(Period period) {
        FinancialDto dto = new FinancialDto();

        dto.setPeriod(period.getName());
        dto.setRevenue(convertService.formatMillions(period.getRevenue()));
        dto.setCostGoodsSold(convertService.formatMillions(period.getCostGoodsSold()));
        dto.setOperatingExpenses(convertService.formatMillions(period.getOperatingExpenses()));
        dto.setNetIncome(convertService.formatMillions(period.getNetIncome()));

        BigDecimal grossProfit = period.getRevenue().subtract(period.getCostGoodsSold());
        dto.setGrossProfit(convertService.formatMillions(grossProfit));

        BigDecimal grossMargin = grossProfit.multiply(new BigDecimal(100)).divide(period.getRevenue(), 2, RoundingMode.HALF_UP);
        dto.setGrossMargin(convertService.formatNoDecimal(grossMargin));

        BigDecimal operatingIncome = grossProfit.subtract(period.getOperatingExpenses());
        dto.setOperatingIncome(convertService.formatMillions(operatingIncome));

        BigDecimal operatingMargin = operatingIncome.multiply(new BigDecimal(100)).divide(period.getRevenue(), 2, RoundingMode.HALF_UP);
        dto.setOperatingMargin(convertService.formatNoDecimal(operatingMargin));

        BigDecimal netMargin = period.getNetIncome().multiply(new BigDecimal(100)).divide(period.getRevenue(), 2, RoundingMode.HALF_UP);
        dto.setNetMargin(convertService.formatNoDecimal(netMargin));

        dto.setDividend(convertService.formatMillions(period.getDividend()));

        return dto;
    }

    private FinancialDto computeTtmFrom(List<Period> periods)
    {
        if (periods.isEmpty()) return null;
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
        return computeFinancialFrom(ttm);
    }

    private List<Period> periodsToQuarters(List<Period> periods)
    {
        List<Period> quarters = new ArrayList<>();
        for (Period period : periods){
            Period quarter = new Period();
            switch (period.getName().substring(2,3)){
                case "F":
                    quarter.setRevenue(period.getRevenue().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setCostGoodsSold(period.getCostGoodsSold().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setOperatingExpenses(period.getOperatingExpenses().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setNetIncome(period.getNetIncome().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarter.setDividend(period.getDividend().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                    quarters.add(quarter);
                    quarters.add(quarter);
                    quarters.add(quarter);
                    quarters.add(quarter);
                    break;
                case "H":
                    quarter.setRevenue(period.getRevenue().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setCostGoodsSold(period.getCostGoodsSold().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setOperatingExpenses(period.getOperatingExpenses().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setNetIncome(period.getNetIncome().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarter.setDividend(period.getDividend().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                    quarters.add(quarter);
                    quarters.add(quarter);
                    break;
                case "Q":
                    quarters.add(period);
                    break;
                default: throw new IllegalArgumentException("Invalid period name: '" + period.getName() + "'");
            }
        }
        return quarters;
    }
}
