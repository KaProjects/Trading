package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.persistence.api.EstimateDao;
import org.kaleta.persistence.entity.Estimate;

import java.util.List;
import java.util.Optional;

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

    @Override
    public Optional<Estimate> findLatest(Long periodId)
    {
        return entityManager.createQuery(
                        selectQuery + "WHERE t.period.id=:periodId ORDER BY t.datetime DESC, t.id DESC",
                        Estimate.class)
                .setParameter("periodId", periodId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }
}
