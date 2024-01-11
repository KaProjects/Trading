package org.kaleta.dao;

import org.kaleta.entity.Record;

import java.util.List;

public interface RecordDao
{
    /**
     * @return lists of records for specified company
     */
    List<Record> list(String companyId);
}
