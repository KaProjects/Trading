package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.entity.Record;

@ApplicationScoped
public class RecordDaoImpl extends EntityCompanyDaoImpl<Record> implements RecordDao
{
    @Override
    protected Class<Record> getEntityClass()
    {
        return Record.class;
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
