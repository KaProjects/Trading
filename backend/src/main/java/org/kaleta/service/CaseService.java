package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.persistence.api.CaseDao;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.api.LatestDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.InvestmentCase;
import org.kaleta.rest.dto.CaseCreateDto;
import org.kaleta.rest.dto.CaseDto;
import org.kaleta.rest.dto.CaseUpdateDto;
import org.kaleta.model.Trades;
import org.kaleta.rest.error.InvalidInputException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class CaseService
{
    public static final int MAX_COMPANY_NAME_LENGTH = 50;

    @Inject
    CaseDao caseDao;
    @Inject
    CompanyDao companyDao;
    @Inject
    CaseEntryService caseEntryService;
    @Inject
    TradeService tradeService;
    @Inject
    LatestDao latestDao;

    public List<CaseDto> getAll()
    {
        List<InvestmentCase> cases = caseDao.listByState();
        Map<Long, Company> companies = companiesOf(cases);
        return cases.stream().map(each -> from(each, companies)).toList();
    }

    public CaseDto get(Long caseId)
    {
        InvestmentCase found = find(caseId);
        CaseDto dto = from(found, companiesOf(List.of(found)));
        dto.setEntries(caseEntryService.getByCaseId(caseId));
        dto.setTrades(tradesOf(found));
        return dto;
    }

    public CaseDto create(CaseCreateDto dto)
    {
        InvestmentCase created = new InvestmentCase();
        created.setCreatedAt(Date.valueOf(LocalDate.now()));
        created.setName(dto.getName().trim());
        // Everything a case is opened with joined it the day it opened.
        Date opened = created.getCreatedAt();
        created.setCompanies(validated(dto.getCompanyIds()).stream()
                .map(companyId -> company(companyId, opened))
                .toList());
        created.setCompanyNames(validatedNames(dto.getCompanyNames()).stream()
                .map(name -> companyName(name, opened))
                .toList());
        caseDao.create(created);
        return get(created.getId());
    }

    public CaseDto update(Long caseId, CaseUpdateDto dto)
    {
        InvestmentCase found = find(caseId);
        if (dto.getName() != null) {
            if (dto.getName().isBlank()) throw new InvalidInputException("case name cannot be blank");
            found.setName(dto.getName().trim());
        }
        if (dto.getCompanies() != null) {
            applyCompanies(found, dto.getCompanies());
        }
        if (dto.getCreatedAt() != null) {
            LocalDate opened = LocalDate.parse(dto.getCreatedAt());
            if (found.getClosedAt() != null && opened.isAfter(found.getClosedAt().toLocalDate())) {
                throw new InvalidInputException("a case cannot open after it was closed");
            }
            found.setCreatedAt(Date.valueOf(opened));
        }
        if (dto.getClosedAt() != null) {
            if (found.getClosedAt() == null) {
                throw new InvalidInputException("case '" + found.getName()
                        + "' is still open, so there is no day it was closed");
            }
            LocalDate closed = LocalDate.parse(dto.getClosedAt());
            if (closed.isBefore(found.getCreatedAt().toLocalDate())) {
                throw new InvalidInputException("a case cannot close before it was opened");
            }
            found.setClosedAt(Date.valueOf(closed));
        }
        if (dto.getClosed() != null) {
            // Closing twice keeps the day it was closed on; opening it again clears it.
            if (!dto.getClosed()) {
                found.setClosedAt(null);
            } else if (found.getClosedAt() == null) {
                found.setClosedAt(Date.valueOf(LocalDate.now()));
            }
        }
        caseDao.save(found);
        return get(caseId);
    }

    /**
     * Only a case that has been closed can be deleted. A case that is still running is being
     * written, and deleting one is not something to do by accident.
     */
    public void delete(Long caseId)
    {
        InvestmentCase found = find(caseId);
        if (found.getClosedAt() == null) {
            throw new InvalidInputException("case '" + found.getName()
                    + "' is still open; close it before deleting it");
        }
        caseDao.delete(caseId);
    }

    /**
     * What the case was actually acted on: the trades its companies opened while it ran. They
     * are not linked to the case by hand - a trade opened in a company the case covers, between
     * the day it opened and the day it closed, is a trade the case is answerable for.
     */
    private List<CaseDto.Trade> tradesOf(InvestmentCase investmentCase)
    {
        LocalDate to = investmentCase.getClosedAt() == null
                ? LocalDate.now()
                : investmentCase.getClosedAt().toLocalDate();

        Map<Long, BigDecimal> prices = latestPrices(investmentCase.getCompanies().stream()
                .map(InvestmentCase.CaseCompany::getCompanyId)
                .toList());

        return investmentCase.getCompanies().stream()
                .flatMap(company -> {
                    // Each company answers for itself from the day it joined the case: one
                    // taken in last week says nothing about what was traded the year before.
                    LocalDate from = company.getAddedAt().toLocalDate();
                    return tradeService.getBy(null, company.getCompanyId(), null, null, null, null)
                            .getTrades().stream()
                            .filter(trade -> {
                                LocalDate purchased = trade.getPurchaseDate().toLocalDate();
                                return !purchased.isBefore(from) && !purchased.isAfter(to);
                            });
                })
                .sorted()
                .map(trade -> {
                    CaseDto.Trade dto = from(trade);
                    // Only a position still open has anything to say about today's price.
                    if (dto.isActive()) dto.setCurrentPrice(prices.get(trade.getCompany().getId()));
                    return dto;
                })
                .toList();
    }

    /** What the app last saw each company trade at, for working out an open position. */
    private Map<Long, BigDecimal> latestPrices(List<Long> companyIds)
    {
        Map<Long, BigDecimal> prices = new LinkedHashMap<>();
        for (Long companyId : companyIds) {
            latestDao.list(companyId).stream().findFirst()
                    .ifPresent(latest -> prices.put(companyId, latest.getPrice()));
        }
        return prices;
    }

    private static CaseDto.Trade from(Trades.Trade trade)
    {
        CaseDto.Trade dto = new CaseDto.Trade();
        dto.setId(trade.getId());
        dto.setTicker(trade.getCompany().getTicker());
        dto.setCurrency(trade.getCompany().getCurrency());
        dto.setActive(trade.isActive());
        dto.setPurchaseDate(trade.getPurchaseDate());
        dto.setPurchasePrice(trade.getPurchasePrice());
        dto.setPurchaseTotal(trade.getPurchaseTotal());
        dto.setQuantity(trade.getPurchaseQuantity());
        dto.setSellDate(trade.getSellDate());
        dto.setSellPrice(trade.getSellPrice());
        dto.setProfit(trade.getProfit());
        dto.setProfitPercentage(trade.getProfitPercentage());
        return dto;
    }

    private InvestmentCase find(Long caseId)
    {
        try {
            return caseDao.get(caseId);
        } catch (NoResultException exception) {
            throw new InvalidInputException("case with id '" + caseId + "' not found");
        }
    }

    /** The companies the case covers, each named once and each one that actually exists. */
    private List<Long> validated(List<Long> companyIds)
    {
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(companyIds == null ? List.of() : companyIds));
        for (Long companyId : distinct) {
            try {
                companyDao.get(companyId);
            } catch (NoResultException exception) {
                throw new InvalidInputException("company with id '" + companyId + "' not found");
            }
        }
        return distinct;
    }

    /**
     * The companies the app does not hold, as written. Each is kept once - the same name twice
     * says nothing twice - and nothing is checked beyond that: the point of a written name is
     * that there is nothing yet to check it against.
     */
    private static List<String> validatedNames(List<String> companyNames)
    {
        Map<String, String> distinct = new LinkedHashMap<>();
        for (String name : companyNames == null ? List.<String>of() : companyNames) {
            if (name == null || name.isBlank()) continue;

            String trimmed = validatedName(name);
            distinct.putIfAbsent(trimmed.toUpperCase(), trimmed);
        }
        return new ArrayList<>(distinct.values());
    }

    private static String validatedName(String name)
    {
        String trimmed = name.trim();
        if (trimmed.length() > MAX_COMPANY_NAME_LENGTH) {
            throw new InvalidInputException("company name '" + trimmed + "' can be at most "
                    + MAX_COMPANY_NAME_LENGTH + " characters long");
        }
        return trimmed;
    }

    /**
     * The companies the case covers from now on. A company that was already in it keeps the day
     * it joined unless another is given; one that is new to the list joins today.
     */
    private void applyCompanies(InvestmentCase found, List<CaseUpdateDto.Company> wanted)
    {
        Map<Long, Date> heldSince = found.getCompanies().stream().collect(Collectors.toMap(
                InvestmentCase.CaseCompany::getCompanyId, InvestmentCase.CaseCompany::getAddedAt,
                (first, second) -> first));
        Map<String, Date> writtenSince = found.getCompanyNames().stream().collect(Collectors.toMap(
                written -> written.getValue().toUpperCase(), InvestmentCase.CaseCompanyName::getAddedAt,
                (first, second) -> first));

        List<InvestmentCase.CaseCompany> companies = new ArrayList<>();
        Map<String, InvestmentCase.CaseCompanyName> names = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>();

        for (CaseUpdateDto.Company company : wanted) {
            if (company.getId() != null) {
                if (ids.contains(company.getId())) continue;

                ids.add(company.getId());
                companies.add(company(company.getId(),
                        dayOf(company.getAddedAt(), heldSince.get(company.getId()))));
            } else {
                if (company.getName() == null || company.getName().isBlank()) continue;

                String written = validatedName(company.getName());
                names.putIfAbsent(written.toUpperCase(), companyName(written,
                        dayOf(company.getAddedAt(), writtenSince.get(written.toUpperCase()))));
            }
        }

        validated(ids);
        found.setCompanies(companies);
        found.setCompanyNames(new ArrayList<>(names.values()));
    }

    /** The day given, else the day it has been in the case since, else today. */
    private static Date dayOf(String given, Date sinceWhen)
    {
        if (given != null) return Date.valueOf(LocalDate.parse(given));

        return sinceWhen != null ? sinceWhen : Date.valueOf(LocalDate.now());
    }

    private static InvestmentCase.CaseCompany company(Long companyId, Date addedAt)
    {
        InvestmentCase.CaseCompany company = new InvestmentCase.CaseCompany();
        company.setCompanyId(companyId);
        company.setAddedAt(addedAt);
        return company;
    }

    private static InvestmentCase.CaseCompanyName companyName(String value, Date addedAt)
    {
        InvestmentCase.CaseCompanyName name = new InvestmentCase.CaseCompanyName();
        name.setValue(value);
        name.setAddedAt(addedAt);
        return name;
    }

    private static CaseDto.Company companyDto(Long id, String ticker, Date addedAt)
    {
        CaseDto.Company company = new CaseDto.Company();
        company.setId(id);
        company.setTicker(ticker);
        company.setAddedAt(addedAt);
        return company;
    }

    private Map<Long, Company> companiesOf(List<InvestmentCase> cases)
    {
        List<Long> ids = cases.stream()
                .flatMap(each -> each.getCompanies().stream())
                .map(InvestmentCase.CaseCompany::getCompanyId)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();

        return companyDao.list().stream()
                .filter(company -> ids.contains(company.getId()))
                .collect(Collectors.toMap(Company::getId, Function.identity()));
    }

    private CaseDto from(InvestmentCase entity, Map<Long, Company> companies)
    {
        CaseDto dto = new CaseDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setClosedAt(entity.getClosedAt());
        // Both kinds read as one list, oldest in the case first: the case grew in this order,
        // and what the app does not hold yet is told apart by having no id rather than by
        // sitting in a list of its own.
        List<CaseDto.Company> covered = new ArrayList<>();
        entity.getCompanies().forEach(held -> {
            Company company = companies.get(held.getCompanyId());
            if (company == null) return;

            covered.add(companyDto(company.getId(), company.getTicker(), held.getAddedAt()));
        });
        entity.getCompanyNames().forEach(written ->
                covered.add(companyDto(null, written.getValue(), written.getAddedAt())));
        covered.sort(java.util.Comparator
                .comparing(CaseDto.Company::getAddedAt)
                .thenComparing(CaseDto.Company::getTicker, String::compareToIgnoreCase));
        dto.setCompanies(covered);
        return dto;
    }
}
