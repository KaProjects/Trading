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

    @Override
    public List<Estimate> findLatestByPeriodIds(List<Long> periodIds)
    {
        if (periodIds.isEmpty()) return List.of();

        return entityManager.createQuery(
                        selectQuery
                                + "WHERE t.period.id IN :periodIds "
                                + "AND NOT EXISTS ("
                                + "SELECT newer.id FROM Estimate newer "
                                + "WHERE newer.period.id=t.period.id "
                                + "AND (newer.datetime > t.datetime "
                                + "OR (newer.datetime=t.datetime AND newer.id > t.id)))",
                        Estimate.class)
                .setParameter("periodIds", periodIds)
                .getResultList();
    }
}
