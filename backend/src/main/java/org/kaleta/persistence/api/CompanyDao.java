package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithAggregates;

import java.util.List;

public interface CompanyDao extends EntityDao<Company>
{
    /**
     * @return list of companies that match provided filters (null filter = all values)
     */
    List<Company> list(String currency, String sector);

    /**
     * @return list of companies with aggregates that match provided filters (null filter = all values)
     */
    List<CompanyWithAggregates> listWithAggregates(String currency, String sector);

    /**
     * @return list of companies with derived stats from related tables
     */
    List<CompanyWithStats> listWithStats();

    /**
     * @return company by Ticker
     */
    Company getByTicker(String ticker);
}
