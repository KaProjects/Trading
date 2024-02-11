package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.kaleta.entity.Dividend;

import java.util.List;

@ApplicationScoped
public class DividendDaoImpl implements DividendDao
{
    @PersistenceContext
    EntityManager entityManager;

    private final String selectQuery = "SELECT d FROM Dividend d";

    @Override
    public List<Dividend> list(String companyId, String currency, String year)
    {
        String joinWord = " WHERE ";
        String companyCondition = "";
        if (companyId != null){
            companyCondition = joinWord + "d.company.id=:companyId";
            joinWord = " AND ";
        }

        String currencyCondition = "";
        if (currency != null){
            currencyCondition = joinWord + "d.company.currency=:currency";
            joinWord = " AND ";
        }

        String yearCondition = "";
        if (year != null){
            yearCondition = joinWord + "YEAR(d.date)=:year";
        }

        TypedQuery<Dividend> query = entityManager.createQuery(selectQuery
                + companyCondition
                + currencyCondition
                + yearCondition, Dividend.class);

        if (companyId != null ) query.setParameter("companyId", companyId);
        if (currency != null ) query.setParameter("currency", currency);
        if (year != null ) query.setParameter("year", year);

        return query.getResultList();
    }

    @Override
    @Transactional
    public void create(Dividend dividend)
    {
        entityManager.persist(dividend);
    }

    @Override
    public Dividend get(String id)
    {
        return entityManager.createQuery(selectQuery + " WHERE d.id=:dividendId", Dividend.class)
                .setParameter("dividendId", id)
                .getSingleResult();
    }
}
