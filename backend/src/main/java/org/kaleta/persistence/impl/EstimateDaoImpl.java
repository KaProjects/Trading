package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.persistence.api.EstimateDao;
import org.kaleta.persistence.entity.Estimate;

import java.util.List;

@ApplicationScoped
public class EstimateDaoImpl extends EntityDaoImpl<Estimate> implements EstimateDao
{
    @Override
    protected Class<Estimate> getEntityClass()
    {
        return Estimate.class;
    }

    @Override
    public List<Estimate> list(Long periodId)
    {
        return entityManager.createQuery(selectQuery + "WHERE t.period.id=:periodId", Estimate.class)
                .setParameter("periodId", periodId)
                .getResultList();
    }
}
