package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import org.kaleta.persistence.api.DividendDao;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Dividend;
import org.kaleta.persistence.entity.Sector;

import java.util.List;

@ApplicationScoped
public class DividendDaoImpl extends EntityCompanyDaoImpl<Dividend> implements DividendDao
{
    @Override
    protected Class<Dividend> getEntityClass()
    {
        return Dividend.class;
    }

    @Override
    public List<Dividend> list(String companyId, String currency, String year, String sector)
    {
        String joinWord = " WHERE ";
        String companyCondition = "";
        if (companyId != null){
            companyCondition = joinWord + "t.company.id=:companyId";
            joinWord = " AND ";
        }

        String currencyCondition = "";
        if (currency != null){
            currencyCondition = joinWord + "t.company.currency=:currency";
            joinWord = " AND ";
        }

        String sectorCondition = "";
        if (sector != null){
            sectorCondition = joinWord + "t.company.sector=:sector";
            joinWord = " AND ";
        }

        String yearCondition = "";
        if (year != null){
            yearCondition = joinWord + "YEAR(t.date)=:year";
        }

        TypedQuery<Dividend> query = entityManager.createQuery(selectQuery
                + companyCondition
                + currencyCondition
                + sectorCondition
                + yearCondition, Dividend.class);

        if (companyId != null ) query.setParameter("companyId", companyId);
        if (currency != null ) query.setParameter("currency", Currency.valueOf(currency));
        if (sector != null ) query.setParameter("sector", Sector.valueOf(sector));
        if (year != null ) query.setParameter("year", year);

        return query.getResultList();
    }
}
