package org.kaleta.dao;

import org.kaleta.entity.Company;

import java.util.List;

public interface CompanyDao
{
    /**
     * @return list of companies
     */
    List<Company> list();
}
