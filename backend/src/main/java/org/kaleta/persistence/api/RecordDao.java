package org.kaleta.persistence.api;

import org.kaleta.model.CompanyInfo;
import org.kaleta.persistence.entity.Record;

import java.util.List;

public interface RecordDao extends EntityCompanyDao<Record>
{
    /**
     * @return latest record dates for every company (that have at least one record)
     */
    List<CompanyInfo> latestRecords();

    /**
     * @return latest strategy dates for every company (that have at least one strategy)
     */
    List<CompanyInfo> latestStrategy();
}
