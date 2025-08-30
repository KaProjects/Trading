package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.kaleta.entity.Period;

import java.util.List;

@ApplicationScoped
public class PeriodDaoImpl implements PeriodDao
{
    @PersistenceContext
    EntityManager entityManager;

    private final String selectQuery = "SELECT p FROM Period p";


    @Override
    public List<Period> list(String companyId)
    {
        return entityManager.createQuery(selectQuery + " WHERE p.company.id=:companyId", Period.class)
                .setParameter("companyId", companyId)
                .getResultList();
    }

    @Override
    public Period get(String periodId)
    {
        return entityManager.createQuery(selectQuery + " WHERE p.id=:periodId", Period.class)
                .setParameter("periodId", periodId)
                .getSingleResult();
    }

    @Transactional
    @Override
    public void save(Period period)
    {
        entityManager.merge(period);
    }

    @Transactional
    @Override
    public void create(Period period)
    {
        entityManager.persist(period);
    }
}
