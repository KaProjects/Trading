package org.kaleta.dao;

import org.kaleta.entity.Record;
import org.kaleta.model.CompanyInfo;

import java.util.List;

@Deprecated
public interface RecordDao
{
    /**
     * @return lists of all records
     */
    List<Record> list();

    /**
     * @return lists of records for specified company
     */
    List<Record> list(String companyId);

    /**
     * @return record according to specified ID
     */
    Record get(String recordId);

    /**
     * @return latest record dates for every company (that have at least one record)
     */
    List<CompanyInfo> latestRecords();

    /**
     * @return latest strategy dates for every company (that have at least one strategy)
     */
    List<CompanyInfo> latestStrategy();

    /**
     * saves the instance of the specified record
     */
    void save(Record record);

    /**
     * creates new record
     */
    void create(Record record);
}
