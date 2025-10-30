package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.kaleta.model.CompanyInfo;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.entity.Record;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RecordDaoImpl extends EntityCompanyDaoImpl<Record> implements RecordDao
{
    @Override
    protected Class<Record> getEntityClass()
    {
        return Record.class;
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

    @Transactional
    @Override
    public void delete(String recordId)
    {
        Record managed = entityManager.find(Record.class, recordId);
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
