package org.kaleta.persistence.impl;

import jakarta.persistence.Query;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithAggregates;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;

import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

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
    public List<CompanyWithAggregates> listWithAggregates(String currency, String sector)
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

        Query query = entityManager.createNativeQuery(
                "SELECT "
                        + "c.id, "
                        + "c.ticker, "
                        + "c.currency, "
                        + "c.watching, "
                        + "c.sector, "
                        + "COALESCE(t.total_trades, 0), "
                        + "COALESCE(t.active_trades, 0), "
                        + "COALESCE(d.dividends, 0), "
                        + "COALESCE(r.records, 0), "
                        + "COALESCE(p.periods, 0) "
                        + "FROM Company c "
                        + "LEFT JOIN ("
                        + "SELECT companyId, COUNT(*) AS total_trades, "
                        + "SUM(CASE WHEN sell_date IS NULL THEN 1 ELSE 0 END) AS active_trades "
                        + "FROM Trade GROUP BY companyId"
                        + ") t ON t.companyId = c.id "
                        + "LEFT JOIN ("
                        + "SELECT companyId, COUNT(*) AS dividends "
                        + "FROM Dividend GROUP BY companyId"
                        + ") d ON d.companyId = c.id "
                        + "LEFT JOIN ("
                        + "SELECT companyId, COUNT(*) AS records "
                        + "FROM Record GROUP BY companyId"
                        + ") r ON r.companyId = c.id "
                        + "LEFT JOIN ("
                        + "SELECT companyId, COUNT(*) AS periods "
                        + "FROM Period GROUP BY companyId"
                        + ") p ON p.companyId = c.id "
                        + currencyCondition
                        + sectorCondition);

        if (currency != null ) query.setParameter("currency", currency);
        if (sector != null ) query.setParameter("sector", sector);

        @SuppressWarnings("unchecked")
        List<Object[]> result = query.getResultList();

        return result.stream().map(this::mapCompanyWithAggregates).collect(Collectors.toList());
    }

    @Override
    public List<CompanyWithStats> listWithStats()
    {
        Query query = entityManager.createNativeQuery(
                "SELECT "
                        + "c.id, "
                        + "c.ticker, "
                        + "c.currency, "
                        + "c.watching, "
                        + "c.sector, "
                        + "p.latest_ending_month, "
                        + "r.latest_record_date, "
                        + "t.latest_purchase_date "
                        + "FROM Company c "
                        + "LEFT JOIN ("
                        + "SELECT companyId, MAX(ending_month) AS latest_ending_month "
                        + "FROM Period WHERE revenue IS NULL GROUP BY companyId"
                        + ") p ON p.companyId = c.id "
                        + "LEFT JOIN ("
                        + "SELECT companyId, MAX(date) AS latest_record_date "
                        + "FROM Record GROUP BY companyId"
                        + ") r ON r.companyId = c.id "
                        + "LEFT JOIN ("
                        + "SELECT companyId, MAX(purchase_date) AS latest_purchase_date "
                        + "FROM Trade WHERE sell_date IS NULL GROUP BY companyId"
                        + ") t ON t.companyId = c.id");

        @SuppressWarnings("unchecked")
        List<Object[]> result = query.getResultList();

        return result.stream().map(this::mapCompanyWithStats).collect(Collectors.toList());
    }

    @Override
    public Company getByTicker(String ticker)
    {
        return entityManager.createQuery(selectQuery + " WHERE t.ticker=:ticker", Company.class)
                .setParameter("ticker", ticker)
                .getSingleResult();
    }

    private CompanyWithAggregates mapCompanyWithAggregates(Object[] values)
    {
        CompanyWithAggregates company = new CompanyWithAggregates();
        company.setId((String) values[0]);
        company.setTicker(asString(values[1]).trim());
        company.setCurrency(Currency.valueOf(asString(values[2])));
        company.setWatching(toBoolean(values[3]));
        if (values[4] != null) {
            company.setSector(Sector.valueOf(asString(values[4])));
        }
        company.setTotalTrades(toInt(values[5]));
        company.setActiveTrades(toInt(values[6]));
        company.setDividends(toInt(values[7]));
        company.setRecords(toInt(values[8]));
        company.setPeriods(toInt(values[9]));
        return company;
    }

    private CompanyWithStats mapCompanyWithStats(Object[] values)
    {
        CompanyWithStats company = new CompanyWithStats();
        company.setId((String) values[0]);
        company.setTicker(asString(values[1]).trim());
        company.setCurrency(Currency.valueOf(asString(values[2])));
        company.setWatching(toBoolean(values[3]));
        if (values[4] != null) {
            company.setSector(Sector.valueOf(asString(values[4])));
        }
        company.setLatestUnreportedPeriodEndingMonth(toYearMonth(values[5]));
        company.setLatestRecordDate((Date) values[6]);
        company.setLatestPurchaseDate((Date) values[7]);
        return company;
    }

    private int toInt(Object value)
    {
        return (value == null) ? 0 : ((Number) value).intValue();
    }

    private boolean toBoolean(Object value)
    {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String asString(Object value)
    {
        return String.valueOf(value);
    }

    private YearMonth toYearMonth(Object value)
    {
        if (value == null) {
            return null;
        }
        String stringValue = asString(value);
        return YearMonth.parse("20" + stringValue.substring(0, 2) + "-" + stringValue.substring(2, 4));
    }
}
