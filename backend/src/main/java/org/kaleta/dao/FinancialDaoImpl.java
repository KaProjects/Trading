package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.kaleta.entity.Financial;

import java.util.List;

@ApplicationScoped
public class FinancialDaoImpl implements FinancialDao
{
    @PersistenceContext
    EntityManager entityManager;

    private final String selectQuery = "SELECT f FROM Financial f";

    @Override
    public List<Financial> list()
    {
        return entityManager.createQuery(selectQuery, Financial.class).getResultList();
    }

    @Override
    public List<Financial> list(String companyId)
    {
        return entityManager.createQuery(selectQuery + " WHERE f.company.id=:companyId", Financial.class)
                .setParameter("companyId", companyId)
                .getResultList();
    }

    @Override
    @Transactional
    public void create(Financial financial)
    {
        entityManager.persist(financial);
    }
}
