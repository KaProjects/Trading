package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.persistence.api.LatestDao;
import org.kaleta.persistence.entity.Latest;

@ApplicationScoped
public class LatestDaoImpl extends EntityCompanyDaoImpl<Latest> implements LatestDao
{
    @Override
    protected Class<Latest> getEntityClass()
    {
        return Latest.class;
    }
}
