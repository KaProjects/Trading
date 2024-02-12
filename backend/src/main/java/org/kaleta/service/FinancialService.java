package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dao.FinancialDao;
import org.kaleta.entity.Financial;

import java.util.List;

@ApplicationScoped
public class FinancialService
{
    @Inject
    FinancialDao financialDao;

    public List<Financial> getFinancials(String companyId)
    {
        return financialDao.list(companyId);
    }
}
