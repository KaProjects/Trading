package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.model.Trades;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.model.CompanyAggregates;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithAggregates;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyUpdateDto;
import org.kaleta.rest.error.InvalidInputException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class CompanyService
{
    @Inject
    CompanyDao companyDao;
    @Inject
    TradeService tradeService;

    public List<org.kaleta.model.Company> getCompanies()
    {
        return companyDao.list().stream().map(this::from).collect(Collectors.toList());
    }

    public List<org.kaleta.model.Company> getCompanies(String currency, String sector)
    {
        return companyDao.list(currency, sector).stream().map(this::from).sorted(org.kaleta.model.Company::compareTo).collect(Collectors.toList());
    }

    public CompanyAggregates getCompaniesWithAggregates(String currency, String sector)
    {
        CompanyAggregates aggregates = new CompanyAggregates();
        aggregates.setCompanies(companyDao.listWithAggregates(currency, sector).stream()
                .map(this::from)
                .collect(Collectors.toList()));
        return aggregates;
    }

    public org.kaleta.model.Company getCompany(Long companyId)
    {
        return from(findEntity(companyId));
    }

    public Company findEntity(Long companyId)
    {
        try {
            return companyDao.get(companyId);
        } catch (NoResultException e){
            throw new InvalidInputException("company with id '" + companyId + "' not found");
        }
    }

    public Map<String, List<CompanyWithStats>> getCompaniesByTag()
    {
        Map<String, List<CompanyWithStats>> companiesByTag = new TreeMap<>();
        YearMonth periodCutoff = YearMonth.now().minusYears(1);
        LocalDate recordCutoff = LocalDate.now().minusYears(1);

        for (CompanyWithStats company : companyDao.listWithStats())
        {
            if (company.getLatestPurchaseDate() != null) {
                companiesByTag.computeIfAbsent("owned", ignored -> new ArrayList<>()).add(company);
            }
            if (company.getLatestPeriodEndingMonth() != null
                    && !company.getLatestPeriodEndingMonth().isBefore(periodCutoff)) {
                companiesByTag.computeIfAbsent("period", ignored -> new ArrayList<>()).add(company);
            }
            if (company.getLatestRecordDate() != null
                    && !company.getLatestRecordDate().toLocalDate().isBefore(recordCutoff)) {
                companiesByTag.computeIfAbsent("record", ignored -> new ArrayList<>()).add(company);
            }
            company.getTags().stream().distinct().forEach(tag ->
                    companiesByTag.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(company));
        }
        return companiesByTag;
    }

    public List<org.kaleta.model.Company> getRecentlyOwnedCompanies()
    {
        Map<Long, org.kaleta.model.Company> recentlyOwnedCompanies = new LinkedHashMap<>();
        for (Trades.Trade trade : tradeService.getRecentlyOwnedTrades()) {
            org.kaleta.model.Company company = trade.getCompany();
            recentlyOwnedCompanies.putIfAbsent(company.getId(), company);
        }
        return new ArrayList<>(recentlyOwnedCompanies.values());
    }

    public void update(CompanyUpdateDto dto)
    {
        Company company;
        try {
            company = companyDao.get(dto.getId());
        } catch (NoResultException e){
            throw new InvalidInputException("company with id '" + dto.getId() + "' not found");
        }

        company.setCurrency(Currency.valueOf(dto.getCurrency()));
        company.setSector((dto.getSector() == null) ? null : Sector.valueOf(dto.getSector()));

        companyDao.save(company);
    }

    public void create(CompanyCreateDto dto)
    {
        try {
            companyDao.getByTicker(dto.getTicker());
            throw new InvalidInputException("company with ticker '" + dto.getTicker() + "' already exists!");
        } catch (NoResultException expected){}

        Company newCompany = new Company();
        newCompany.setTicker(dto.getTicker());
        newCompany.setCurrency(Currency.valueOf(dto.getCurrency()));
        newCompany.setSector((dto.getSector() == null) ? null : Sector.valueOf(dto.getSector()));

        companyDao.create(newCompany);
    }

    public org.kaleta.model.Company from(Company entity){
        org.kaleta.model.Company company = new org.kaleta.model.Company();
        company.setId(entity.getId());
        company.setTicker(entity.getTicker());
        company.setCurrency(entity.getCurrency());
        company.setTags(new ArrayList<>(entity.getTags()));
        if (entity.getSector() != null) {
            company.setSector(new org.kaleta.model.Company.Sector(entity.getSector()));
        }
        return company;
    }

    private CompanyAggregates.Company from(CompanyWithAggregates entity)
    {
        CompanyAggregates.Company company = new CompanyAggregates.Company();
        company.setId(entity.getId());
        company.setTicker(entity.getTicker());
        company.setCurrency(entity.getCurrency());
        if (entity.getSector() != null) {
            company.setSector(new org.kaleta.model.Company.Sector(entity.getSector()));
        }
        company.setTotalTrades(entity.getTotalTrades());
        company.setActiveTrades(entity.getActiveTrades());
        company.setDividends(entity.getDividends());
        company.setRecords(entity.getRecords());
        company.setPeriods(entity.getPeriods());
        return company;
    }
}
