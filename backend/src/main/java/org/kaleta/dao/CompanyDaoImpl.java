package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
}
