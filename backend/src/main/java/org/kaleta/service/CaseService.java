package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.persistence.api.CaseDao;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.InvestmentCase;
import org.kaleta.rest.dto.CaseCreateDto;
import org.kaleta.rest.dto.CaseDto;
import org.kaleta.rest.dto.CaseUpdateDto;
import org.kaleta.model.Trades;
import org.kaleta.rest.error.InvalidInputException;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class CaseService
{
    @Inject
    CaseDao caseDao;
    @Inject
    CompanyDao companyDao;
    @Inject
    CaseEntryService caseEntryService;
    @Inject
    TradeService tradeService;

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
        created.setCompanyIds(validated(dto.getCompanyIds()));
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
        if (dto.getCompanyIds() != null) {
            found.setCompanyIds(validated(dto.getCompanyIds()));
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

    public void delete(Long caseId)
    {
        find(caseId);
        caseDao.delete(caseId);
    }

    /**
     * What the case was actually acted on: the trades its companies opened while it ran. They
     * are not linked to the case by hand - a trade opened in a company the case covers, between
     * the day it opened and the day it closed, is a trade the case is answerable for.
     */
    private List<CaseDto.Trade> tradesOf(InvestmentCase investmentCase)
    {
        LocalDate from = investmentCase.getCreatedAt().toLocalDate();
        LocalDate to = investmentCase.getClosedAt() == null
                ? LocalDate.now()
                : investmentCase.getClosedAt().toLocalDate();

        return investmentCase.getCompanyIds().stream()
                .flatMap(companyId -> tradeService.getBy(null, companyId, null, null, null, null)
                        .getTrades().stream())
                .filter(trade -> {
                    LocalDate purchased = trade.getPurchaseDate().toLocalDate();
                    return !purchased.isBefore(from) && !purchased.isAfter(to);
                })
                .sorted()
                .map(CaseService::from)
                .toList();
    }

    private static CaseDto.Trade from(Trades.Trade trade)
    {
        CaseDto.Trade dto = new CaseDto.Trade();
        dto.setId(trade.getId());
        dto.setTicker(trade.getCompany().getTicker());
        dto.setActive(trade.isActive());
        dto.setPurchaseDate(trade.getPurchaseDate());
        dto.setPurchasePrice(trade.getPurchasePrice());
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

    private Map<Long, Company> companiesOf(List<InvestmentCase> cases)
    {
        List<Long> ids = cases.stream().flatMap(each -> each.getCompanyIds().stream()).distinct().toList();
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
        dto.setCompanies(entity.getCompanyIds().stream()
                .map(companies::get)
                .filter(java.util.Objects::nonNull)
                .map(company -> {
                    CaseDto.Company linked = new CaseDto.Company();
                    linked.setId(company.getId());
                    linked.setTicker(company.getTicker());
                    return linked;
                })
                .sorted(java.util.Comparator.comparing(CaseDto.Company::getTicker))
                .toList());
        return dto;
    }
}
