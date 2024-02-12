package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dao.FinancialDao;
import org.kaleta.model.FinancialsModel;

@ApplicationScoped
public class FinancialService
{
    @Inject
    FinancialDao financialDao;

    public FinancialsModel getFinancialsModel(String companyId)
    {
        return new FinancialsModel(financialDao.list(companyId));
    }
}
