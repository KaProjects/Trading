package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dao.RecordDao;
import org.kaleta.entity.Record;

import java.util.List;

@ApplicationScoped
public class RecordService
{
    @Inject
    RecordDao recordDao;

    public List<Record> getRecords(String companyId)
    {
        return recordDao.list(companyId);
    }
}
