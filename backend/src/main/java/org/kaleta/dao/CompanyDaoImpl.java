package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.kaleta.entity.Company;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Sector;

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
    public List<Company> list(String currency, String sector)
    {
        String joinWord = " WHERE ";

        String currencyCondition = "";
        if (currency != null){
            currencyCondition = joinWord + "c.currency=:currency";
            joinWord = " AND ";
        }

        String sectorCondition = "";
        if (sector != null){
            sectorCondition = joinWord + "c.sector=:sector";
        }

        TypedQuery<Company> query = entityManager.createQuery(selectQuery
                + currencyCondition
                + sectorCondition, Company.class);

        if (currency != null ) query.setParameter("currency", Currency.valueOf(currency));
        if (sector != null ) query.setParameter("sector", Sector.get(sector));

        return query.getResultList();
    }

    @Override
    public Company get(String companyId)
    {
        return entityManager.createQuery(selectQuery + " WHERE c.id=:companyId", Company.class)
                .setParameter("companyId", companyId)
                .getSingleResult();
    }

    @Override
    public Company getByTicker(String ticker)
    {
        return entityManager.createQuery(selectQuery + " WHERE c.ticker=:ticker", Company.class)
                .setParameter("ticker", ticker)
                .getSingleResult();
    }

    @Override
    @Transactional
    public void save(Company company)
    {
        entityManager.merge(company);
    }

    @Override
    @Transactional
    public void create(Company company)
    {
        entityManager.persist(company);
    }
}
