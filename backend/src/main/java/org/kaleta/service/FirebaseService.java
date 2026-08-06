package org.kaleta.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import io.quarkus.logging.Log;
import org.kaleta.firebase.FirebaseStore;
import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.Trades;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.EstimateImportDto;
import org.kaleta.rest.dto.PeriodImportCandidateDto;
import org.kaleta.rest.dto.PeriodImportDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Singleton
public class FirebaseService
{
    private final FirebaseStore firebaseStore;

    @Inject
    public FirebaseService(FirebaseStore firebaseStore)
    {
        this.firebaseStore = firebaseStore;
    }

    public record ImportCandidatesResult(
            List<PeriodImportCandidateDto> periods,
            List<String> warnings)
    {
        public ImportCandidatesResult
        {
            periods = List.copyOf(periods);
            warnings = List.copyOf(warnings);
        }
    }

    public void pushAssets(Trades activeTrades)
    {
        firebaseStore.replaceAssets(activeTrades.getTrades().stream()
                .map(FirebaseAsset::from)
                .collect(Collectors.toList()));
    }

    public ImportCandidatesResult getNewerPeriods(String ticker, String quarterId)
    {
        PeriodName latestPeriod = quarterId == null ? null : PeriodName.valueOf(quarterId);
        List<String> warnings = new ArrayList<>();
        Set<String> quarterIds;
        try {
            quarterIds = firebaseStore.findQuarterIds(ticker);
        } catch (RuntimeException exception) {
            String warning = ExternalWarnings.unavailable(
                    "Firebase import candidates for " + ticker,
                    exception);
            Log.warn(warning, exception);
            return new ImportCandidatesResult(List.of(), List.of(warning));
        }

        List<PeriodImportCandidateDto> periods = new ArrayList<>();
        for (String id : quarterIds) {
            try {
                if (latestPeriod == null || PeriodName.valueOf(id).compareTo(latestPeriod) > 0) {
                    periods.add(toImportCandidate(ticker, id));
                }
            } catch (RuntimeException exception) {
                String warning = ExternalWarnings.unavailable(
                        "Firebase period " + id + " for " + ticker,
                        exception);
                Log.warn(warning, exception);
                warnings.add(warning);
            }
        }
        periods.sort(Comparator.comparing(
                candidate -> PeriodName.valueOf(candidate.getName()),
                Comparator.reverseOrder()));
        return new ImportCandidatesResult(periods, warnings);
    }

    private PeriodImportCandidateDto toImportCandidate(String ticker, String quarterId)
    {
        FirebaseStore.QuarterMetadata metadata = firebaseStore.findQuarterMetadata(ticker, quarterId);
        PeriodImportCandidateDto candidate = new PeriodImportCandidateDto();
        candidate.setName(quarterId);
        candidate.setEndingMonth(YearMonth.parse("20" + metadata.endingMonth()).toString());
        candidate.setIsReported(metadata.reported());
        return candidate;
    }

    public PeriodImportDto getPeriod(String ticker, String quarterId)
    {
        return firebaseStore.findQuarter(ticker, quarterId)
                .map(FirebaseCompany.Gemini.Quarter::toImportDto)
                .orElse(null);
    }

    public EstimateImportDto.Quarter getLatestEstimate(String ticker, String quarterId)
    {
        FirebaseCompany.FinnhubEarnings latest = getLatestEarnings(ticker, quarterId);
        if (latest == null) return null;

        EstimateImportDto.Quarter quarter = new EstimateImportDto.Quarter();
        quarter.setEps(firstNonBlank(latest.getEpsa(), latest.getEpse()));
        quarter.setDate(reportDate(latest.getReport()));
        return quarter;
    }

    public String getLatestActualEps(String ticker, String quarterId)
    {
        FirebaseCompany.FinnhubEarnings latest = getLatestEarnings(ticker, quarterId);
        if (latest == null || latest.getEpsa() == null || latest.getEpsa().isBlank()) return null;
        return latest.getEpsa();
    }

    public void updatePeriod(Period period)
    {
        String ticker = period.getCompany().getTicker();
        String quarterId = period.getName().toString();
        if (firebaseStore.findQuarter(ticker, quarterId).isEmpty()) return;

        FirebaseCompany.Gemini.Quarter quarter = new FirebaseCompany.Gemini.Quarter();
        quarter.setReport_date_this_quarter(toString(period.getReportDate()));
        quarter.setReported_shares(toString(period.getShares()));
        quarter.setPrice_min(toString(period.getPriceLow()));
        quarter.setPrice_max(toString(period.getPriceHigh()));
        quarter.setReported_revenues(toString(period.getRevenue()));
        quarter.setReported_gross_profit(toString(period.getGrossProfit()));
        quarter.setReported_operating_income(toString(period.getOperatingIncome()));
        quarter.setReported_net_income(toString(period.getNetIncome()));
        quarter.setReported_div(toString(period.getDividend()));
        quarter.setReported_eps(toString(period.getAdjustedEps()));

        firebaseStore.updateQuarter(ticker, quarterId, quarter);
    }

    private FirebaseCompany.FinnhubEarnings getLatestEarnings(String ticker, String quarterId)
    {
        Map<String, FirebaseCompany.FinnhubEarnings> estimates = firebaseStore.findEarnings(ticker, quarterId);
        if (estimates == null || estimates.isEmpty()) return null;

        return estimates.entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private String toString(Object object)
    {
        return object == null ? "" : String.valueOf(object);
    }

    private String firstNonBlank(String preferred, String fallback)
    {
        if (preferred != null && !preferred.isBlank()) return preferred;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return null;
    }

    private String reportDate(String report)
    {
        if (report == null || report.length() < 10) return null;
        try {
            return LocalDate.parse(report.substring(0, 10)).toString();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
