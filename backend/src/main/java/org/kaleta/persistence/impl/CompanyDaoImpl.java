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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public List<Company> list()
    {
        return entityManager.createQuery(
                "SELECT DISTINCT t FROM Company t LEFT JOIN FETCH t.tags",
                Company.class
        ).getResultList();
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
                        + "c.alphaVantageTicker, "
                        + "c.name, "
                        + "c.description, "
                        + "c.logoUrl, "
                        + "c.website, "
                        + "c.currency, "
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
                        + "c.alphaVantageTicker, "
                        + "c.currency, "
                        + "c.sector, "
                        + "p.latest_ending_month, "
                        + "r.latest_record_date, "
                        + "t.latest_purchase_date, "
                        + "tag.value "
                        + "FROM Company c "
                        + "LEFT JOIN ("
                        + "SELECT companyId, MAX(ending_month) AS latest_ending_month "
                        + "FROM Period GROUP BY companyId"
                        + ") p ON p.companyId = c.id "
                        + "LEFT JOIN ("
                        + "SELECT companyId, MAX(date) AS latest_record_date "
                        + "FROM Record GROUP BY companyId"
                        + ") r ON r.companyId = c.id "
                        + "LEFT JOIN ("
                        + "SELECT companyId, MAX(purchase_date) AS latest_purchase_date "
                        + "FROM Trade WHERE sell_date IS NULL GROUP BY companyId"
                        + ") t ON t.companyId = c.id "
                        + "LEFT JOIN Tag tag ON tag.companyId = c.id");

        @SuppressWarnings("unchecked")
        List<Object[]> result = query.getResultList();

        Map<Long, CompanyWithStats> companies = new LinkedHashMap<>();
        for (Object[] values : result) {
            Long companyId = ((Number) values[0]).longValue();
            CompanyWithStats company = companies.computeIfAbsent(companyId, ignored -> mapCompanyWithStats(values));
            if (values[8] != null) {
                company.getTags().add(asString(values[8]));
            }
        }
        return List.copyOf(companies.values());
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
        company.setId(((Number) values[0]).longValue());
        company.setTicker(asString(values[1]).trim());
        company.setAlphaVantageTicker(nullableString(values[2]));
        company.setName(nullableString(values[3]));
        company.setDescription(nullableString(values[4]));
        company.setLogoUrl(nullableString(values[5]));
        company.setWebsite(nullableString(values[6]));
        company.setCurrency(Currency.valueOf(asString(values[7])));
        if (values[8] != null) {
            company.setSector(Sector.valueOf(asString(values[8])));
        }
        company.setTotalTrades(toInt(values[9]));
        company.setActiveTrades(toInt(values[10]));
        company.setDividends(toInt(values[11]));
        company.setRecords(toInt(values[12]));
        company.setPeriods(toInt(values[13]));
        return company;
    }

    private CompanyWithStats mapCompanyWithStats(Object[] values)
    {
        CompanyWithStats company = new CompanyWithStats();
        company.setId(((Number) values[0]).longValue());
        company.setTicker(asString(values[1]).trim());
        company.setAlphaVantageTicker(nullableString(values[2]));
        company.setCurrency(Currency.valueOf(asString(values[3])));
        if (values[4] != null) {
            company.setSector(Sector.valueOf(asString(values[4])));
        }
        company.setLatestPeriodEndingMonth(toYearMonth(values[5]));
        company.setLatestRecordDate(toDate(values[6]));
        company.setLatestPurchaseDate(toDate(values[7]));
        return company;
    }

    private int toInt(Object value)
    {
        return (value == null) ? 0 : ((Number) value).intValue();
    }

    private String asString(Object value)
    {
        return String.valueOf(value);
    }

    private String nullableString(Object value)
    {
        return value == null ? null : asString(value).trim();
    }

    private Date toDate(Object value)
    {
        if (value == null || value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof LocalDate localDate) {
            return Date.valueOf(localDate);
        }
        throw new IllegalArgumentException("Unsupported date value: " + value.getClass().getName());
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
