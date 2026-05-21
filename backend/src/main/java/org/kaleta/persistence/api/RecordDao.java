package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.Record;

public interface RecordDao extends EntityCompanyDao<Record>
{
    /**
     * removes the instance specified by id.
     */
    void delete(String recordId);
}
