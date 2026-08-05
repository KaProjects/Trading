package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.model.CompanyAggregates;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithAggregates;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyTagCreateDto;
import org.kaleta.rest.dto.CompanyUpdateDto;
import org.kaleta.rest.error.InvalidInputException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class CompanyService
{
    private static final Comparator<String> COMPANY_LIST_ORDER = Comparator
            .comparingInt(CompanyService::companyListOrder)
            .thenComparing(Comparator.naturalOrder());

    @Inject
    CompanyDao companyDao;

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
        Map<String, List<CompanyWithStats>> companiesByTag = new TreeMap<>(COMPANY_LIST_ORDER);
        List<CompanyWithStats> allCompanies = new ArrayList<>();
        YearMonth periodCutoff = YearMonth.now().minusYears(1);
        LocalDate recordCutoff = LocalDate.now().minusYears(1);

        for (CompanyWithStats company : companyDao.listWithStats())
        {
            allCompanies.add(company);
            if (company.getLatestPurchaseDate() != null) {
                companiesByTag.computeIfAbsent("owned", ignored -> new ArrayList<>()).add(company);
            }
            if (company.getLatestPeriodEndingMonth() != null
                    && !company.getLatestPeriodEndingMonth().isBefore(periodCutoff)) {
                companiesByTag.computeIfAbsent("researched", ignored -> new ArrayList<>()).add(company);
            }
            if (company.getLatestRecordDate() != null
                    && !company.getLatestRecordDate().toLocalDate().isBefore(recordCutoff)) {
                companiesByTag.computeIfAbsent("recent", ignored -> new ArrayList<>()).add(company);
            }
            company.getTags().stream().distinct().forEach(tag ->
                    companiesByTag.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(company));
        }
        companiesByTag.put("all", allCompanies);
        return companiesByTag;
    }

    private static int companyListOrder(String tag)
    {
        return switch (tag) {
            case "owned" -> 0;
            case "recent" -> 1;
            case "researched" -> 2;
            case "all" -> 4;
            default -> 3;
        };
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

    public void addTag(CompanyTagCreateDto dto)
    {
        Company company = findEntity(dto.getCompanyId());

        if (!company.getTags().contains(dto.getValue())) {
            company.getTags().add(dto.getValue());
            companyDao.save(company);
        }
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
