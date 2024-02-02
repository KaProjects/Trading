package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.dao.CompanyDao;
import org.kaleta.dao.DividendDao;
import org.kaleta.dto.DividendCreateDto;
import org.kaleta.entity.Company;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Dividend;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.kaleta.Utils.format;

@ApplicationScoped
public class DividendService
{
    @Inject
    DividendDao dividendDao;
    @Inject
    CompanyDao companyDao;

    public List<Dividend> getDividends(String company, String currency, String year)
    {
        return dividendDao.list(company, currency, year);
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
        try {
            Company company = companyDao.get(dto.getCompanyId());
            newDividend.setCompany(company);
        } catch (NoResultException e){
            throw new ServiceFailureException("company with id '" + dto.getCompanyId() + "' not found");
        }
        if (Utils.isValidDbDate(dto.getDate())){
            newDividend.setDate(Date.valueOf(dto.getDate()));
        } else {
            throw new ServiceFailureException("invalid date format '" + dto.getDate() + "' not YYYY-MM-DD");
        }
        newDividend.setDividend(new BigDecimal(dto.getDividend()));
        newDividend.setTax(new BigDecimal(dto.getTax()));

        dividendDao.create(newDividend);

        return dividendDao.get(newDividend.getId());
    }
}
