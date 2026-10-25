package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.kaleta.model.TargetStats;
import org.kaleta.persistence.api.TargetDao;
import org.kaleta.persistence.entity.Target;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class TargetDaoImpl extends EntityDaoImpl<Target> implements TargetDao
{
    @Override
    protected Class<Target> getEntityClass()
    {
        return Target.class;
    }

    @Override
    public List<Target> list(Long periodId)
    {
        return entityManager.createQuery(
                        selectQuery + "WHERE t.period.id=:periodId ORDER BY t.date DESC, t.id DESC",
                        Target.class)
                .setParameter("periodId", periodId)
                .getResultList();
    }

    @Override
    public List<Target> listByPeriodIds(List<Long> periodIds)
    {
        if (periodIds.isEmpty()) return List.of();

        return entityManager.createQuery(
                        selectQuery + "WHERE t.period.id IN :periodIds ORDER BY t.period.id, t.date DESC, t.id DESC",
                        Target.class)
                .setParameter("periodIds", periodIds)
                .getResultList();
    }

    @Override
    public Optional<Target> findByIdentity(
            Long periodId,
            Date date,
            String institution,
            BigDecimal price)
    {
        return entityManager.createQuery(
                        selectQuery
                                + "WHERE t.period.id=:periodId "
                                + "AND t.date=:date "
                                + "AND t.institution=:institution "
                                + "AND t.price=:price",
                        Target.class)
                .setParameter("periodId", periodId)
                .setParameter("date", date)
                .setParameter("institution", institution)
                .setParameter("price", price)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Map<Long, TargetStats> statistics(List<Long> periodIds)
    {
        if (periodIds.isEmpty()) return Map.of();

        List<Object[]> rows = entityManager.createQuery(
                        "SELECT t.period.id, COUNT(t), MIN(t.price), SUM(t.price), MAX(t.price) "
                                + "FROM Target t "
                                + "WHERE t.period.id IN :periodIds "
                                + "GROUP BY t.period.id",
                        Object[].class)
                .setParameter("periodIds", periodIds)
                .getResultList();

        Map<Long, TargetStats> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            long count = ((Number) row[1]).longValue();
            BigDecimal sum = (BigDecimal) row[3];
            result.put(
                    ((Number) row[0]).longValue(),
                    new TargetStats(
                            count,
                            (BigDecimal) row[2],
                            sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP),
                            (BigDecimal) row[4]));
        }
        return Map.copyOf(result);
    }

    @Transactional
    @Override
    public void createAll(List<Target> targets)
    {
        targets.forEach(entityManager::persist);
    }

    @Transactional
    @Override
    public void delete(Long targetId)
    {
        Target managed = entityManager.find(Target.class, targetId);
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
