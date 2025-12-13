package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.model.CompanyInfo;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyUpdateDto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class CompanyService
{
    @Inject
    CompanyDao companyDao;
    @Inject
    RecordDao recordDao;
    @Inject
    TradeDao tradeDao;

    public List<org.kaleta.model.Company> getCompanies()
    {
        return companyDao.list().stream().map(this::from).collect(Collectors.toList());
    }

    public List<org.kaleta.model.Company> getCompanies(String currency, String sector)
    {
        return companyDao.list(currency, sector).stream().map(this::from).sorted(org.kaleta.model.Company::compareTo).collect(Collectors.toList());
    }

    public org.kaleta.model.Company getCompany(String companyId)
    {
        return from(findEntity(companyId));
    }

    public Company findEntity(String companyId)
    {
        try {
            return companyDao.get(companyId);
        } catch (NoResultException e){
            throw new ServiceFailureException("company with id '" + companyId + "' not found");
        }
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
            info.setSector(company.getSector());
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

    public void update(CompanyUpdateDto dto)
    {
        Company company;
        try {
            company = companyDao.get(dto.getId());
        } catch (NoResultException e){
            throw new ServiceFailureException("company with id '" + dto.getId() + "' not found");
        }

        company.setCurrency(Currency.valueOf(dto.getCurrency()));
        company.setWatching(Boolean.parseBoolean(dto.getWatching()));
        company.setSector((dto.getSector() == null) ? null : Sector.valueOf(dto.getSector()));

        companyDao.save(company);
    }

    public void create(CompanyCreateDto dto)
    {
        try {
            companyDao.getByTicker(dto.getTicker());
            throw new ServiceFailureException("Company with ticker '" + dto.getTicker() + "' already exists!");
        } catch (NoResultException ignored){}

        Company newCompany = new Company();
        newCompany.setTicker(dto.getTicker());
        newCompany.setCurrency(Currency.valueOf(dto.getCurrency()));
        newCompany.setWatching(Boolean.parseBoolean(dto.getWatching()));
        newCompany.setSector((dto.getSector() == null) ? null : Sector.valueOf(dto.getSector()));

        companyDao.create(newCompany);
    }

    private org.kaleta.model.Company from(Company entity){
        org.kaleta.model.Company company = new org.kaleta.model.Company();
        company.setId(entity.getId());
        company.setTicker(entity.getTicker());
        company.setCurrency(entity.getCurrency());
        company.setWatching(entity.isWatching());
        if (entity.getSector() != null) {
            company.setSector(new org.kaleta.model.Company.Sector(entity.getSector()));
        }
        return company;
    }
}
