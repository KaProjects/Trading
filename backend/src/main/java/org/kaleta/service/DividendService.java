package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dao.DividendDao;
import org.kaleta.dto.DividendCreateDto;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Dividend;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.kaleta.Utils.format;

@ApplicationScoped
public class DividendService
{
    @Inject
    DividendDao dividendDao;
    @Inject
    CompanyService companyService;
    @Inject
    ConvertService convertService;

    public List<Dividend> getDividends(String company, String currency, String year, String sector)
    {
        return dividendDao.list(company, currency, year, sector);
    }

    public String[] computeSums(List<Dividend> dividends)
    {
        Set<String> companies = new HashSet<>();
        Set<Currency> currencies = new HashSet<>();
        BigDecimal dividendsSum = new BigDecimal("0.00");
        BigDecimal taxesSum = new BigDecimal("0.00");
        BigDecimal totalSum = new BigDecimal("0.00");
        for (Dividend dividend : dividends)
        {
            companies.add(dividend.getTicker());
            currencies.add(dividend.getCurrency());
            dividendsSum = dividendsSum.add(dividend.getDividend());
            taxesSum = taxesSum.add(dividend.getTax());
            totalSum = totalSum.add(dividend.getTotal());
        }
        return new String[]{String.valueOf(companies.size()), String.valueOf(currencies.size()), "",
                format(dividendsSum), format(taxesSum), format(totalSum)};
    }

    public Dividend createDividend(DividendCreateDto dto)
    {
        Dividend newDividend = new Dividend();

        newDividend.setCompany(companyService.getCompany(dto.getCompanyId()));
        newDividend.setDate(convertService.parse(dto.getDate()));
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
}
