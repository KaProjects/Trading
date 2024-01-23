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
     * @return company by ID
     */
    Company get(String companyId);

    /**
     * saves the instance of the specified company
     */
    void store(Company company);
}
