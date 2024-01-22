package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.kaleta.entity.Trade;
import org.kaleta.model.CompanyInfo;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class TradeDaoImpl implements TradeDao
{
    @PersistenceContext
    EntityManager entityManager;

    private final String selectQuery = "SELECT t FROM Trade t";

    @Override
    public List<Trade> list(Boolean active, String companyId, String currency, String year)
    {
        String joinWord = " WHERE ";
        String activeCondition = "";
        if (active != null){
            if (active){
                activeCondition = joinWord + "t.sellDate IS NULL";
            } else {
                activeCondition = joinWord + "t.sellDate IS NOT NULL";
            }
            joinWord = " AND ";
        }

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

        String yearCondition = "";
        if (year != null){
            yearCondition = joinWord + "(CONVERT(YEAR(t.purchaseDate),CHAR(4))=:year OR (t.sellDate IS NOT NULL AND CONVERT(YEAR(t.sellDate),CHAR(4))=:year))";
        }

        TypedQuery<Trade> query = entityManager.createQuery(selectQuery
                + activeCondition
                + companyCondition
                + currencyCondition
                + yearCondition, Trade.class);

        if (companyId != null ) query.setParameter("companyId", companyId);
        if (currency != null ) query.setParameter("currency", currency);
        if (year != null ) query.setParameter("year", year);

        return query.getResultList();
    }

    @Override
    public List<CompanyInfo> latestPurchase()
    {
        List<Object[]> objs = entityManager.createNativeQuery("SELECT companyId, MAX(purchase_date) FROM Trade WHERE sell_date IS NULL GROUP BY companyId").getResultList();
        List<CompanyInfo> infos = new ArrayList<>();
        for (Object[] values : objs){
            CompanyInfo info = new CompanyInfo();
            info.setId((String) values[0]);
            info.setLatestPurchaseDate((Date) values[1]);
            infos.add(info);
        }
        return infos;
    }
}
