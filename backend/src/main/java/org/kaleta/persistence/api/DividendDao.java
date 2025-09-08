package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.Dividend;

import java.util.List;

public interface DividendDao extends SuperDao<Dividend>
{
    /**
     * @return lists of dividends that match provided filters (null filter = all values)
     */
    List<Dividend> list(String companyId, String currency, String year, String sector);
}
