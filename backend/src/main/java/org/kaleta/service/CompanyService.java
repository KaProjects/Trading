package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.kaleta.client.AlphaVantageClient;
import org.kaleta.client.PolygonClient;
import org.kaleta.client.RequestFailureException;
import org.kaleta.client.dto.AlphaVantageTicker;
import org.kaleta.client.dto.PolygonCompanyProfile;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.model.CompanyAggregates;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithAggregates;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Exchange;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class CompanyService
{
    private static final Set<String> RESERVED_TAGS = Set.of("owned", "recent", "researched", "all");
    private static final Comparator<String> COMPANY_LIST_ORDER = Comparator
            .comparingInt(CompanyService::companyListOrder)
            .thenComparing(Comparator.naturalOrder());

    @Inject
    CompanyDao companyDao;
    @Inject
    AlphaVantageClient alphaVantageClient;
    @Inject
    PolygonClient polygonClient;

    public List<AlphaVantageTicker> findAlphaVantageTickers(String ticker, String currency)
    {
        Currency expectedCurrency = Currency.valueOf(currency);
        try {
            return alphaVantageClient.searchTickers(ticker).stream()
                    .filter(candidate -> "Equity".equalsIgnoreCase(candidate.type()))
                    .filter(candidate -> expectedCurrency.getIsoCode().equalsIgnoreCase(candidate.currency()))
                    .sorted(Comparator
                            .comparingInt((AlphaVantageTicker candidate) ->
                                    baseSymbol(candidate.symbol()).equalsIgnoreCase(ticker) ? 0 : 1)
                            .thenComparing(
                                    AlphaVantageTicker::matchScore,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(AlphaVantageTicker::symbol))
                    .toList();
        } catch (RequestFailureException exception) {
            throw new InvalidInputException("Alpha Vantage ticker search failed: " + exception.getMessage());
        }
    }

    private static String baseSymbol(String symbol)
    {
        int suffix = symbol.indexOf('.');
        return suffix < 0 ? symbol : symbol.substring(0, suffix);
    }

    public PolygonCompanyProfile getPolygonCompanyProfile(String ticker)
    {
        try {
            return polygonClient.getCompanyProfile(ticker)
                    .orElseThrow(() -> new InvalidInputException(
                            "Polygon.io company data for ticker '" + ticker + "' was not found"));
        } catch (RequestFailureException exception) {
            throw new InvalidInputException(
                    "Polygon.io company data for ticker '" + ticker + "' could not be loaded: "
                            + exception.getMessage());
        }
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
        company.setAlphaVantageTicker(dto.getAlphaVantageTicker());
        setExchange(company, dto.getExchange());
        company.setName(nullableTrimmed(dto.getName()));
        company.setDescription(nullableTrimmed(dto.getDescription()));
        company.setLogoUrl(nullableTrimmed(dto.getLogoUrl()));
        company.setWebsite(nullableTrimmed(dto.getWebsite()));

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
        newCompany.setAlphaVantageTicker(dto.getAlphaVantageTicker());
        setExchange(newCompany, dto.getExchange());
        newCompany.setName(nullableTrimmed(dto.getName()));
        newCompany.setDescription(nullableTrimmed(dto.getDescription()));
        newCompany.setLogoUrl(nullableTrimmed(dto.getLogoUrl()));
        newCompany.setWebsite(nullableTrimmed(dto.getWebsite()));
        newCompany.setCurrency(Currency.valueOf(dto.getCurrency()));
        newCompany.setSector((dto.getSector() == null) ? null : Sector.valueOf(dto.getSector()));

        companyDao.create(newCompany);
    }

    public void addTag(CompanyTagCreateDto dto)
    {
        validateCustomTag(dto.getValue());

        Company company = findEntity(dto.getCompanyId());

        if (company.getTags().stream().anyMatch(tag -> tag.equalsIgnoreCase(dto.getValue()))) {
            throw new InvalidInputException("tag '" + dto.getValue() + "' is already assigned to company '"
                    + company.getTicker() + "'");
        }

        company.getTags().add(dto.getValue());
        companyDao.save(company);
    }

    @Transactional
    public void removeTag(Long companyId, String value)
    {
        validateCustomTag(value);

        Company company = findEntity(companyId);
        String assignedTag = company.getTags().stream()
                .filter(tag -> tag.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("tag '" + value
                        + "' is not assigned to company '" + company.getTicker() + "'"));

        company.getTags().remove(assignedTag);
        companyDao.save(company);
    }

    private static void validateCustomTag(String value)
    {
        if (RESERVED_TAGS.stream().anyMatch(tag -> tag.equalsIgnoreCase(value))) {
            throw new InvalidInputException("tag '" + value + "' is reserved");
        }
    }

    private static String nullableTrimmed(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void setExchange(Company company, String exchangeValue)
    {
        company.setExchange(exchangeValue == null ? null : Exchange.valueOf(exchangeValue));
    }

    public org.kaleta.model.Company from(Company entity){
        org.kaleta.model.Company company = new org.kaleta.model.Company();
        company.setId(entity.getId());
        company.setTicker(entity.getTicker());
        company.setAlphaVantageTicker(entity.getAlphaVantageTicker());
        company.setExchange(entity.getExchange() == null
                ? null
                : new org.kaleta.model.Company.Exchange(entity.getExchange()));
        company.setName(entity.getName());
        company.setDescription(entity.getDescription());
        company.setLogoUrl(entity.getLogoUrl());
        company.setWebsite(entity.getWebsite());
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
        company.setAlphaVantageTicker(entity.getAlphaVantageTicker());
        company.setExchange(entity.getExchange() == null
                ? null
                : new org.kaleta.model.Company.Exchange(entity.getExchange()));
        company.setName(entity.getName());
        company.setDescription(entity.getDescription());
        company.setLogoUrl(entity.getLogoUrl());
        company.setWebsite(entity.getWebsite());
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
