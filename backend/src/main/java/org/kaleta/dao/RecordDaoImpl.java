package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.kaleta.entity.Record;
import org.kaleta.model.CompanyInfo;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RecordDaoImpl implements RecordDao
{
    @PersistenceContext
    EntityManager entityManager;

    private final String selectQuery = "SELECT r FROM Record r";

    @Override
    public List<Record> list(String companyId)
    {
        return entityManager.createQuery(selectQuery + " WHERE r.company.id=:companyId", Record.class)
                .setParameter("companyId", companyId)
                .getResultList();
    }

    @Override
    public Record get(String recordId)
    {
        return entityManager.createQuery(selectQuery + " WHERE r.id=:recordId", Record.class)
                .setParameter("recordId", recordId)
                .getSingleResult();
    }

    @Override
    public List<CompanyInfo> latestRecords()
    {
        List<Object[]> objs = entityManager.createNativeQuery("SELECT companyId, MAX(date) FROM Record GROUP BY companyId").getResultList();
        List<CompanyInfo> infos = new ArrayList<>();
        for (Object[] values : objs){
            CompanyInfo info = new CompanyInfo();
            info.setId((String) values[0]);
            info.setLatestReviewDate((Date) values[1]);
            infos.add(info);
        }
        return infos;
    }

    @Override
    public List<CompanyInfo> latestStrategy()
    {
        List<Object[]> objs = entityManager.createNativeQuery("SELECT companyId, MAX(date) FROM Record WHERE strategy IS NOT NULL GROUP BY companyId").getResultList();
        List<CompanyInfo> infos = new ArrayList<>();
        for (Object[] values : objs){
            CompanyInfo info = new CompanyInfo();
            info.setId((String) values[0]);
            info.setLatestStrategyDate((Date) values[1]);
            infos.add(info);
        }
        return infos;
    }

    @Override
    @Transactional
    public void save(Record record)
    {
        entityManager.merge(record);
    }

    @Override
    @Transactional
    public void create(Record record)
    {
        entityManager.persist(record);
    }
}
