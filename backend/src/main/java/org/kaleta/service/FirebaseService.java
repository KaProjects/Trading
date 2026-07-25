package org.kaleta.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.kaleta.firebase.FirebaseStore;
import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.FirebaseCompanyDep;
import org.kaleta.model.Trades;
import org.kaleta.persistence.entity.Period;
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.error.InvalidInputException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class FirebaseService
{
    private final FirebaseStore firebaseStore;

    @Inject
    public FirebaseService(FirebaseStore firebaseStore)
    {
        this.firebaseStore = firebaseStore;
    }

    public boolean hasCompany(String ticker)
    {
        return firebaseStore.findCompanyDep(ticker).isPresent();
    }

    public FirebaseCompanyDep getCompanyDep(String ticker)
    {
        return firebaseStore.findCompanyDep(ticker)
                .orElseThrow(() -> new InvalidInputException("company with ticker '" + ticker + "' not found"));
    }

    public void pushAssets(Trades activeTrades)
    {
        firebaseStore.replaceAssets(activeTrades.getTrades().stream()
                .map(FirebaseAsset::from)
                .collect(Collectors.toList()));
    }

    public List<PeriodImportDto> getNewerPeriods(String ticker, String quarterId)
    {
        FirebaseCompany company = firebaseStore.findCompany(ticker).orElse(null);
        if (company == null || company.getGemini() == null || company.getGemini().getQuarters() == null) {
            return new ArrayList<>();
        }
        return company.getGemini().getQuarters().values().stream()
                .filter(quarter -> quarter.isInFutureOf(quarterId))
                .sorted(Comparator.comparing(FirebaseCompany.Gemini.Quarter::getId).reversed())
                .map(FirebaseCompany.Gemini.Quarter::toImportDto)
                .collect(Collectors.toList());
    }

    public PeriodImportDto getPeriod(String ticker, String quarterId)
    {
        FirebaseCompany company = firebaseStore.findCompany(ticker).orElse(null);
        if (company == null || company.getGemini() == null || company.getGemini().getQuarters() == null) return null;
        FirebaseCompany.Gemini.Quarter quarter = company.getGemini().getQuarters().get(quarterId);
        if (quarter == null) return null;
        return quarter.toImportDto();
    }

    public void updatePeriod(Period period)
    {
        String ticker = period.getCompany().getTicker();
        FirebaseCompany company = firebaseStore.findCompany(ticker).orElse(null);
        if (company == null || company.getGemini() == null || company.getGemini().getQuarters() == null) return;
        String quarterId = period.getName().toString();
        FirebaseCompany.Gemini.Quarter quarter = company.getGemini().getQuarters().get(quarterId);
        if (quarter == null) return;

        quarter.setReport_date_this_quarter(toString(period.getReportDate()));
        quarter.setReported_shares(toString(period.getShares()));
        quarter.setPrice_min(toString(period.getPriceLow()));
        quarter.setPrice_max(toString(period.getPriceHigh()));
        quarter.setReported_revenues(toString(period.getRevenue()));
        quarter.setReported_gross_profit(toString(period.getGrossProfit()));
        quarter.setReported_operating_income(toString(period.getOperatingIncome()));
        quarter.setReported_net_income(toString(period.getNetIncome()));
        quarter.setReported_div(toString(period.getDividend()));

        firebaseStore.saveQuarter(ticker, quarterId, quarter);
    }

    private String toString(Object object)
    {
        return object == null ? "" : String.valueOf(object);
    }
}
