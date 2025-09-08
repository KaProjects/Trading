package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;

import java.util.List;

@ApplicationScoped
public class CompanyDaoImpl extends EntityDaoImpl<Company> implements CompanyDao
{
    @Override
    protected Class<Company> getEntityClass()
    {
        return Company.class;
    }

    @Override
    public List<Company> list(String currency, String sector)
    {
        String joinWord = " WHERE ";

        String currencyCondition = "";
        if (currency != null){
            currencyCondition = joinWord + "t.currency=:currency";
            joinWord = " AND ";
        }

        String sectorCondition = "";
        if (sector != null){
            sectorCondition = joinWord + "t.sector=:sector";
        }

        TypedQuery<Company> query = entityManager.createQuery(selectQuery
                + currencyCondition
                + sectorCondition, Company.class);

        if (currency != null ) query.setParameter("currency", Currency.valueOf(currency));
        if (sector != null ) query.setParameter("sector", Sector.valueOf(sector));

        return query.getResultList();
    }

    @Override
    public Company getByTicker(String ticker)
    {
        return entityManager.createQuery(selectQuery + " WHERE t.ticker=:ticker", Company.class)
                .setParameter("ticker", ticker)
                .getSingleResult();
    }
}
