package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.dao.CompanyDao;
import org.kaleta.dao.RecordDao;
import org.kaleta.dao.TradeDao;
import org.kaleta.dto.CompanyDto;
import org.kaleta.entity.Company;
import org.kaleta.model.CompanyInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public List<Company> getCompanies(String currency, String sector)
    {
        return companyDao.list(currency, sector);
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

    public void updateCompany(CompanyDto companyDto)
    {
        Company company = getCompany(companyDto.getId());

        if (companyDto.getWatching() != null) company.setWatching(companyDto.getWatching());

        companyDao.store(company);
    }

    public Company getCompany(String companyId)
    {
        try {
            return companyDao.get(companyId);
        } catch (NoResultException e){
            throw new ServiceFailureException("company with id '" + companyId + "' not found");
        }
    }

    public String computeMarketCap(String price, String sharesFloat)
    {
        String suffix = sharesFloat.substring(sharesFloat.length() - 1);
        BigDecimal shares = new BigDecimal(sharesFloat.substring(0, sharesFloat.length() - 1));
        BigDecimal marketCap = shares.multiply(new BigDecimal(price));
        if (marketCap.compareTo(new BigDecimal(1000)) > 0){
            marketCap = marketCap.divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);
            switch (suffix){
                case "B": return Utils.format(marketCap) + "T";
                case "M": return Utils.format(marketCap) + "B";
                default: throw new IllegalStateException("invalid shares float suffix: '" + suffix + "'");
            }
        } else {
            return Utils.format(marketCap) + suffix;
        }
    }
}
