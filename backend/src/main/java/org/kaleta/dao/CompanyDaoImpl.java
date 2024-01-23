package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.kaleta.entity.Company;

import java.util.List;

@ApplicationScoped
public class CompanyDaoImpl implements CompanyDao
{
    @PersistenceContext
    EntityManager entityManager;

    private final String selectQuery = "SELECT c FROM Company c";


    @Override
    public List<Company> list()
    {
        return entityManager.createQuery(selectQuery, Company.class).getResultList();
    }

    @Override
    public Company get(String companyId)
    {
        return entityManager.createQuery(selectQuery + " WHERE c.id=:companyId", Company.class)
                .setParameter("companyId", companyId)
                .getSingleResult();
    }

    @Override
    @Transactional
    public void store(Company company)
    {
        entityManager.merge(company);
    }
}
