package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.Company;

import java.util.List;

public interface CompanyDao extends EntityDao<Company>
{
    /**
     * @return list of companies that match provided filters (null filter = all values)
     */
    List<Company> list(String currency, String sector);

    /**
     * @return company by Ticker
     */
    Company getByTicker(String ticker);
}
