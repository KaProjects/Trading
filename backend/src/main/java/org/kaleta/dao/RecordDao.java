package org.kaleta.dao;

import org.kaleta.entity.Record;

import java.util.List;

public interface RecordDao
{
    /**
     * @return lists of records for specified company
     */
    List<Record> list(String companyId);

    /**
     * @return record according to specified record ID
     */
    Record get(String recordId);

    /**
     * saves the instance of the specified record
     */
    void store(Record record);
}
