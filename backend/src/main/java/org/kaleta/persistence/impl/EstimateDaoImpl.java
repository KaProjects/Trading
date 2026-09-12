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
    public List<Estimate> list(Long periodId, boolean type)
    {
        return entityManager.createQuery(
                        selectQuery + "WHERE t.period.id=:periodId AND t.type=:type "
                                + "ORDER BY t.datetime DESC, t.id DESC",
                        Estimate.class)
                .setParameter("periodId", periodId)
                .setParameter("type", type)
                .getResultList();
    }

    @Override
    public Optional<Estimate> findLatest(Long periodId, boolean type)
    {
        return entityManager.createQuery(
                        selectQuery + "WHERE t.period.id=:periodId AND t.type=:type "
                                + "ORDER BY t.datetime DESC, t.id DESC",
                        Estimate.class)
                .setParameter("periodId", periodId)
                .setParameter("type", type)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Estimate> findLatestByPeriodIds(List<Long> periodIds, boolean type)
    {
        if (periodIds.isEmpty()) return List.of();

        return entityManager.createQuery(
                        selectQuery
                                + "WHERE t.period.id IN :periodIds "
                                + "AND t.type=:type "
                                + "AND NOT EXISTS ("
                                + "SELECT newer.id FROM Estimate newer "
                                + "WHERE newer.period.id=t.period.id "
                                + "AND newer.type=t.type "
                                + "AND (newer.datetime > t.datetime "
                                + "OR (newer.datetime=t.datetime AND newer.id > t.id)))",
                        Estimate.class)
                .setParameter("periodIds", periodIds)
                .setParameter("type", type)
                .getResultList();
    }
}
