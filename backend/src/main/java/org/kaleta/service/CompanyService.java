package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.dao.CompanyDao;
import org.kaleta.dao.RecordDao;
import org.kaleta.dao.TradeDao;
import org.kaleta.dto.CompanyDto;
import org.kaleta.entity.Company;
import org.kaleta.model.CompanyInfo;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CompanyService
{
    @Inject
    CompanyDao companyDao;

    @Inject
    RecordDao recordDao;

    @Inject
    TradeDao tradeDao;

    public List<Company> getCompanies()
    {
        return companyDao.list();
    }

    public List<CompanyInfo> getCompaniesInfo()
    {
        List<CompanyInfo> companiesInfo = new ArrayList<>();
        for (Company company : companyDao.list())
        {
            CompanyInfo info = new CompanyInfo();
            info.setId(company.getId());
            info.setTicker(company.getTicker());
            info.setWatching(company.isWatching());
            companiesInfo.add(info);
        }
        for (CompanyInfo record : recordDao.latestRecords()) {
            for (CompanyInfo info : companiesInfo) {
                if (info.getId().equals(record.getId())){
                    info.setLatestReviewDate(record.getLatestReviewDate());
                }
            }
        }
        for (CompanyInfo record : recordDao.latestStrategy()) {
            for (CompanyInfo info : companiesInfo) {
                if (info.getId().equals(record.getId())){
                    info.setLatestStrategyDate(record.getLatestStrategyDate());
                }
            }
        }
        for (CompanyInfo trade : tradeDao.latestPurchase()) {
            for (CompanyInfo info : companiesInfo) {
                if (info.getId().equals(trade.getId())){
                    info.setLatestPurchaseDate(trade.getLatestPurchaseDate());
                }
            }
        }
        return companiesInfo;
    }

    public void updateCompany(CompanyDto companyDto)
    {
        Company company;
        try {
            company = companyDao.get(companyDto.getId());
        } catch (NoResultException e){
            throw new ServiceException("company with id '" + companyDto.getId() + "' not found");
        }
        if (companyDto.getWatching() != null) company.setWatching(companyDto.getWatching());

        companyDao.store(company);
    }

    public Company getCompany(String companyId)
    {
        try {
            return companyDao.get(companyId);
        } catch (NoResultException e){
            throw new ServiceException("company with id '" + companyId + "' not found");
        }
    }
}
