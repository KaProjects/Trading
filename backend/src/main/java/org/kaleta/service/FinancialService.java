package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dao.FinancialDao;
import org.kaleta.dto.FinancialCreateDto;
import org.kaleta.entity.Financial;
import org.kaleta.model.FinancialsModel;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * @return aggregates map <companyId, [financials count]>
     */
    public Map<String, int[]> getCompanyAggregates()
    {
        Map<String, int[]> map = new HashMap<>();
        for (Financial financial : financialDao.list())
        {
            String companyId = financial.getCompany().getId();
            int[] aggregates = map.containsKey(companyId) ? map.get(companyId) : new int[]{0};
            aggregates[0] = aggregates[0] + 1;
            map.put(companyId, aggregates);
        }
        return map;
    }
}
