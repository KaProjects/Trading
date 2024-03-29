package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.model.CompanyInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class RecordsUiCompanyListsDto
{
    private List<Company> watchingOldestReview = new ArrayList<>();
    private List<Company> ownedWithoutStrategy = new ArrayList<>();
    private List<Company> notWatching = new ArrayList<>();
    private Map<String, List<Company>> sectors = new HashMap<>();

    @Data
    public static class Company
    {
        private String id;
        private String ticker;
        private boolean watching;
        private String latestReviewDate;
        private String latestPurchaseDate; //only owned
        private String latestStrategyDate;
    }

    public void addWatchingOldestReview(List<CompanyInfo> companiesInfo)
    {
        for (CompanyInfo info : companiesInfo)
        {
            if (info.isWatching()) watchingOldestReview.add(from(info));
        }
        watchingOldestReview.sort((companyA, companyB) -> Utils.compareDtoDates(companyA.getLatestReviewDate(), companyB.getLatestReviewDate()));
    }

    public void addOwnedWithoutStrategy(List<CompanyInfo> companiesInfo)
    {
        for (CompanyInfo info : companiesInfo)
        {
            if (info.isWatching() && info.getLatestPurchaseDate() != null){
                Company dto = from(info);
                if (Utils.compareDtoDates(dto.getLatestPurchaseDate(), dto.getLatestStrategyDate()) > 0)
                {
                    ownedWithoutStrategy.add(dto);
                }
            }
        }
        ownedWithoutStrategy.sort((companyA, companyB) -> Utils.compareDtoDates(companyA.getLatestPurchaseDate(), companyB.getLatestPurchaseDate()));
    }

    public void addNotWatching(List<CompanyInfo> companiesInfo)
    {
        for (CompanyInfo info : companiesInfo)
        {
            if (!info.isWatching()) notWatching.add(from(info));
        }
        notWatching.sort(Comparator.comparing(Company::getTicker));
    }

    public void addSectors(List<CompanyInfo> companiesInfo)
    {
        for (CompanyInfo companyInfo : companiesInfo) {
            if (companyInfo.getSector() != null) {
                if (!sectors.containsKey(companyInfo.getSector().getName())) sectors.put(companyInfo.getSector().getName(), new ArrayList<>());
                sectors.get(companyInfo.getSector().getName()).add(from(companyInfo));
            }
        }
    }

    private Company from(CompanyInfo companyInfo)
    {
        Company dto = new Company();
        dto.setId(companyInfo.getId());
        dto.setWatching(companyInfo.isWatching());
        dto.setTicker(companyInfo.getTicker());
        if (companyInfo.getLatestReviewDate() != null)
            dto.setLatestReviewDate(Utils.format(companyInfo.getLatestReviewDate()));
        if (companyInfo.getLatestStrategyDate() != null)
            dto.setLatestStrategyDate(Utils.format(companyInfo.getLatestStrategyDate()));
        if (companyInfo.getLatestPurchaseDate() != null)
            dto.setLatestPurchaseDate(Utils.format(companyInfo.getLatestPurchaseDate()));
        return dto;
    }
}
