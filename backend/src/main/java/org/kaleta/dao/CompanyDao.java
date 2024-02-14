package org.kaleta.dao;

import org.kaleta.entity.Company;

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
     * saves the instance of the specified company
     */
    void store(Company company);
}
