package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Period;

@ApplicationScoped
public class PeriodDaoImpl extends EntityCompanyDaoImpl<Period> implements PeriodDao
{
    @Override
    protected Class<Period> getEntityClass()
    {
        return Period.class;
    }
}
