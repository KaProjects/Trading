package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dto.DividendCreateDto;
import org.kaleta.model.Company;
import org.kaleta.model.Dividends;
import org.kaleta.model.PeriodFrequency;
import org.kaleta.persistence.api.DividendDao;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Dividend;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class DividendService
{
    @Inject
    DividendDao dividendDao;
    @Inject
    CompanyService companyService;

    @Deprecated // use model
    public List<Dividend> getDividends(String company, String currency, String year, String sector)
    {
        return dividendDao.list(company, currency, year, sector);
    }

    public Dividends getBy(String company, String currency, String year, String sector)
    {
        List<Dividend> dividends = dividendDao.list(company, currency, year, sector);

        Dividends model = new Dividends();

        model.setDividends(dividends.stream().map(this::from).sorted(Dividends.Dividend::compareTo).collect(Collectors.toList()));

        model.setAggregates(computeAggregates(model.getDividends()));

        return model;
    }

    public Map<Company, List<Dividends.Dividend>> getByCompany(String currency, String year, String sector) {
        Dividends dividends = getBy(null, currency, year, sector);
        Map<Company, List<Dividends.Dividend>> map = new HashMap<>();
        for (Dividends.Dividend dividend : dividends.getDividends()) {
            if (!map.containsKey(dividend.getCompany())) {
                map.put(dividend.getCompany(), new ArrayList<>());
            }
            map.get(dividend.getCompany()).add(dividend);
        }
        return map;
    }

    public Map<String, List<Dividends.Dividend>> getByPeriod(PeriodFrequency frequency, String companyId, String currency, String sector) {
        Dividends dividends = getBy(companyId, currency, null, sector);
        Map<String, List<Dividends.Dividend>> map = new HashMap<>();
        for (Dividends.Dividend dividend : dividends.getDividends()) {
            String period = frequency.parseDate(dividend.getDate().toString());
            if (!map.containsKey(period)) {
                for (String key : frequency.getAllYearKeys(dividend.getDate().toString())) {
                    map.put(key, new ArrayList<>());
                }
            }
            map.get(period).add(dividend);
        }
        return map;
    }

    public Dividend createDividend(DividendCreateDto dto)
    {
        Dividend newDividend = new Dividend();

        newDividend.setCompany(companyService.findEntity(dto.getCompanyId()));
        newDividend.setDate(Date.valueOf(dto.getDate()));
        newDividend.setDividend(new BigDecimal(dto.getDividend()));
        newDividend.setTax(new BigDecimal(dto.getTax()));

        dividendDao.create(newDividend);

        return dividendDao.get(newDividend.getId());
    }

    /**
     * @return aggregates map <companyId, [dividends count]>
     */
    public Map<String, int[]> getCompanyAggregates()
    {
        Map<String, int[]> map = new HashMap<>();
        for (Dividend dividend : dividendDao.list(null, null, null, null))
        {
            String companyId = dividend.getCompany().getId();
            int[] aggregates = map.containsKey(companyId) ? map.get(companyId) : new int[]{0};
            aggregates[0] = aggregates[0] + 1;
            map.put(companyId, aggregates);
        }
        return map;
    }

    private Dividends.Aggregates computeAggregates(List<Dividends.Dividend> dividends)
    {
        Set<String> companies = new HashSet<>();
        Set<Currency> currencies = new HashSet<>();
        BigDecimal dividendSum = BigDecimal.ZERO;
        BigDecimal taxSum = BigDecimal.ZERO;
        BigDecimal netSum = BigDecimal.ZERO;
        for (Dividends.Dividend dividend : dividends) {
            companies.add(dividend.getCompany().getTicker());
            currencies.add(dividend.getCompany().getCurrency());
            dividendSum = dividendSum.add(dividend.getDividend());
            taxSum = taxSum.add(dividend.getTax());
            netSum = netSum.add(dividend.getNet());
        }
        Dividends.Aggregates aggregates = new Dividends.Aggregates();
        aggregates.setCompanies(companies.size());
        aggregates.setCurrencies(currencies.size());
        aggregates.setDividendSum(dividendSum);
        aggregates.setTaxSum(taxSum);
        aggregates.setNetSum(netSum);
        return aggregates;
    }

    private Dividends.Dividend from(Dividend entity) {
        Dividends.Dividend dividend = new Dividends.Dividend();
        dividend.setId(entity.getId());
        dividend.setCompany(companyService.from(entity.getCompany()));
        dividend.setDate(entity.getDate());
        dividend.setDividend(entity.getDividend());
        dividend.setTax(entity.getTax());
        dividend.setNet(entity.getDividend().subtract(entity.getTax()));
        return dividend;
    }
}
