package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.Company;

import java.util.List;

public interface CompanyDao
{
    /**
     * @return list of companies
     */
    List<Company> list();

    /**
     * @return list of companies that match provided filters (null filter = all values)
     */
    List<Company> list(String currency, String sector);

    /**
     * @return company by ID
     */
    Company get(String companyId);

    /**
     * @return company by Ticker
     */
    Company getByTicker(String ticker);

    /**
     * saves the instance of the specified company
     */
    void save(Company company);

    /**
     * creates new company
     */
    void create(Company company);
}
