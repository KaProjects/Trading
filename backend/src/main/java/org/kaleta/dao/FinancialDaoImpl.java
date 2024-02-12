package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.kaleta.entity.Financial;

import java.util.List;

@ApplicationScoped
public class FinancialDaoImpl implements FinancialDao
{
    @PersistenceContext
    EntityManager entityManager;

    private final String selectQuery = "SELECT f FROM Financial f";


    @Override
    public List<Financial> list(String companyId)
    {
        return entityManager.createQuery(selectQuery + " WHERE f.company.id=:companyId", Financial.class)
                .setParameter("companyId", companyId)
                .getResultList();
    }
}
