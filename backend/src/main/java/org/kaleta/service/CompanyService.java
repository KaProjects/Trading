package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dao.CompanyDao;
import org.kaleta.entity.Company;

import java.util.List;

@ApplicationScoped
public class CompanyService
{
    @Inject
    CompanyDao companyDao;

    public List<Company> getCompanies()
    {
        return companyDao.list();
    }
}
