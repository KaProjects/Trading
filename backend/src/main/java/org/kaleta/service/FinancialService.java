package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dao.FinancialDao;
import org.kaleta.dto.FinancialCreateDto;
import org.kaleta.entity.Financial;
import org.kaleta.model.FinancialsModel;

import java.math.BigDecimal;

@ApplicationScoped
public class FinancialService
{
    @Inject
    FinancialDao financialDao;
    @Inject
    CompanyService companyService;

    public FinancialsModel getFinancialsModel(String companyId)
    {
        return new FinancialsModel(financialDao.list(companyId));
    }

    public void createFinancial(FinancialCreateDto dto)
    {
        Financial newFinancial = new Financial();

        newFinancial.setCompany(companyService.getCompany(dto.getCompanyId()));
        newFinancial.setQuarter(dto.getQuarter());
        newFinancial.setRevenue(new BigDecimal(dto.getRevenue()));
        newFinancial.setNetIncome(new BigDecimal(dto.getNetIncome()));
        newFinancial.setEps(new BigDecimal(dto.getEps()));

        financialDao.create(newFinancial);
    }
}
