package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.persistence.api.FinancialDao;
import org.kaleta.persistence.entity.Financial;

@ApplicationScoped
public class FinancialDaoImpl extends EntityCompanyDaoImpl<Financial> implements FinancialDao
{
    @Override
    protected Class<Financial> getEntityClass()
    {
        return Financial.class;
    }
}
