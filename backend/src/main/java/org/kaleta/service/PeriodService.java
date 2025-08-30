package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.dao.PeriodDao;
import org.kaleta.dto.PeriodCreateDto;
import org.kaleta.dto.PeriodDto;
import org.kaleta.dto.PeriodsDto;
import org.kaleta.entity.Company;
import org.kaleta.entity.Period;

import java.math.BigDecimal;
import java.util.List;

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
        if (dto.getReportDate() != null) period.setReportDate(convertService.parse(dto.getReportDate()));
        if (dto.getShares() != null) period.setShares(dto.getShares());
        if (dto.getPriceLatest() != null) period.setPriceLatest(new BigDecimal(dto.getPriceLatest()));
        if (dto.getPriceHigh() != null) period.setPriceHigh(new BigDecimal(dto.getPriceHigh()));
        if (dto.getPriceLow() != null) period.setPriceLow(new BigDecimal(dto.getPriceLow()));
        if (dto.getResearch() != null) period.setResearch(dto.getResearch());
        if (dto.getRevenue() != null) period.setRevenue(new BigDecimal(dto.getRevenue()));
        if (dto.getCostGoodsSold() != null) period.setCostGoodsSold(new BigDecimal(dto.getCostGoodsSold()));
        if (dto.getOperatingExpenses() != null) period.setOperatingExpenses(new BigDecimal(dto.getOperatingExpenses()));
        if (dto.getNetIncome() != null) period.setNetIncome(new BigDecimal(dto.getNetIncome()));

        periodDao.save(period);
    }

    public void create(PeriodCreateDto dto)
    {
        Period period = new Period();

        period.setCompany(companyService.getCompany(dto.getCompanyId()));
        period.setName(dto.getName());
        period.setEndingMonth(dto.getEndingMonth());
        if (dto.getReportDate() != null) period.setReportDate(convertService.parse(dto.getReportDate()));

        periodDao.create(period);
    }

    public PeriodsDto getBy(String companyId)
    {
        Company company = companyService.getCompany(companyId);
        List<Period> periods = periodDao.list(companyId);
        periods.sort((a, b) -> -Utils.compareEndingMonths(a.getEndingMonth(), b.getEndingMonth()));

        PeriodsDto dto = new PeriodsDto();
        dto.setCompany(companyService.from(company));
        for (Period period : periods) {
            dto.getPeriods().add(from(period));
        }

        return dto;
    }

    public PeriodDto from(Period period)
    {
        PeriodDto dto = new PeriodDto();
        dto.setId(period.getId());
        dto.setName(period.getName());
        dto.setEndingMonth(period.getEndingMonth());
        dto.setReportDate(convertService.format(period.getReportDate()));
        dto.setShares(period.getShares());
        dto.setPriceLatest(convertService.format(period.getPriceLatest()));
        dto.setPriceHigh(convertService.format(period.getPriceHigh()));
        dto.setPriceLow(convertService.format(period.getPriceLow()));
        dto.setResearch(period.getResearch());
        dto.setRevenue(convertService.formatMillions(period.getRevenue()));
        dto.setCostGoodsSold(convertService.formatMillions(period.getCostGoodsSold()));
        dto.setOperatingExpenses(convertService.formatMillions(period.getOperatingExpenses()));
        dto.setNetIncome(convertService.formatMillions(period.getNetIncome()));
        return dto;
    }
}
