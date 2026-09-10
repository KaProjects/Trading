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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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

    public record TargetsResult(
            List<FirebaseCompany.Gemini.Target> targets,
            List<String> warnings)
    {
        public TargetsResult
        {
            targets = List.copyOf(targets);
            warnings = List.copyOf(warnings);
        }
    }

    public record AllCompaniesResult(
            Map<String, FirebaseCompany> companies,
            List<String> warnings)
    {
        public AllCompaniesResult
        {
            companies = Collections.unmodifiableMap(new LinkedHashMap<>(companies));
            warnings = List.copyOf(warnings);
        }
    }

    public record NewsSentimentsResult(
            Map<String, FirebaseCompany.NewsSentiment> records,
            List<String> warnings)
    {
        public NewsSentimentsResult
        {
            records = Collections.unmodifiableMap(new LinkedHashMap<>(records));
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
        Map<String, FirebaseStore.QuarterMetadata> quarters;
        try {
            quarters = firebaseStore.findQuartersMetadata(ticker);
        } catch (RuntimeException exception) {
            String warning = ExternalWarnings.unavailable(
                    "Firebase import candidates for " + ticker,
                    exception);
            Log.warn(warning, exception);
            return new ImportCandidatesResult(List.of(), List.of(warning));
        }
        return buildImportCandidates(quarters, quarterId, ticker);
    }

    public ImportCandidatesResult getNewerPeriods(FirebaseCompany company, String quarterId)
    {
        return buildImportCandidates(quarterMetadataOf(company), quarterId, null);
    }

    public TargetsResult getTargets(String ticker)
    {
        try {
            return new TargetsResult(
                    firebaseStore.findTargets(ticker).values().stream()
                            .filter(java.util.Objects::nonNull)
                            .toList(),
                    List.of());
        } catch (RuntimeException exception) {
            String warning = ExternalWarnings.unavailable(
                    "Firebase targets for " + ticker,
                    exception);
            Log.warn(warning, exception);
            return new TargetsResult(List.of(), List.of(warning));
        }
    }

    public TargetsResult getTargets(FirebaseCompany company)
    {
        if (company == null || company.getGemini() == null || company.getGemini().getTargets() == null) {
            return new TargetsResult(List.of(), List.of());
        }
        return new TargetsResult(
                company.getGemini().getTargets().values().stream()
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                List.of());
    }

    public AllCompaniesResult getAllCompanies()
    {
        try {
            return new AllCompaniesResult(firebaseStore.findAllCompanies(), List.of());
        } catch (RuntimeException exception) {
            String warning = ExternalWarnings.unavailable("Firebase company data", exception);
            Log.warn(warning, exception);
            return new AllCompaniesResult(Map.of(), List.of(warning));
        }
    }

    private ImportCandidatesResult buildImportCandidates(
            Map<String, FirebaseStore.QuarterMetadata> quarters,
            String quarterId,
            String ticker)
    {
        PeriodName latestPeriod = quarterId == null ? null : PeriodName.valueOf(quarterId);
        List<String> warnings = new ArrayList<>();
        List<PeriodImportCandidateDto> periods = new ArrayList<>();
        for (Map.Entry<String, FirebaseStore.QuarterMetadata> quarter : quarters.entrySet()) {
            String id = quarter.getKey();
            try {
                if (latestPeriod == null || PeriodName.valueOf(id).compareTo(latestPeriod) > 0) {
                    periods.add(toImportCandidate(id, quarter.getValue()));
                }
            } catch (RuntimeException exception) {
                String warning = ExternalWarnings.unavailable(
                        "Firebase period " + id + (ticker != null ? " for " + ticker : ""),
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

    private Map<String, FirebaseStore.QuarterMetadata> quarterMetadataOf(FirebaseCompany company)
    {
        if (company == null || company.getGemini() == null || company.getGemini().getQuarters() == null) {
            return Map.of();
        }
        return company.getGemini().getQuarters().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> {
                            FirebaseCompany.Gemini.Quarter quarter = entry.getValue();
                            String revenues = quarter.getReported_revenues();
                            return new FirebaseStore.QuarterMetadata(
                                    quarter.getEnding_month(),
                                    revenues != null && !revenues.isBlank());
                        }));
    }

    public NewsSentimentsResult getNewsSentiments(
            String ticker,
            LocalDate startInclusive,
            LocalDate endExclusive)
    {
        try {
            return new NewsSentimentsResult(
                    firebaseStore.findNewsSentiments(ticker, startInclusive, endExclusive),
                    List.of());
        } catch (RuntimeException exception) {
            return unavailableNewsSentiments(ticker, exception);
        }
    }

    public NewsSentimentsResult getLatestNewsSentiments(String ticker)
    {
        try {
            return new NewsSentimentsResult(
                    firebaseStore.findLatestNewsSentiments(ticker),
                    List.of());
        } catch (RuntimeException exception) {
            return unavailableNewsSentiments(ticker, exception);
        }
    }

    private NewsSentimentsResult unavailableNewsSentiments(String ticker, RuntimeException exception)
    {
        String warning = ExternalWarnings.unavailable(
                "Firebase news sentiment for " + ticker,
                exception);
        Log.warn(warning, exception);
        return new NewsSentimentsResult(Map.of(), List.of(warning));
    }

    private PeriodImportCandidateDto toImportCandidate(
            String quarterId,
            FirebaseStore.QuarterMetadata metadata)
    {
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

    public String getReportingCurrency(String ticker)
    {
        return firebaseStore.findGeminiInfo(ticker)
                .map(FirebaseCompany.Gemini.Info::getCurrency)
                .orElse(null);
    }

    public EstimateImportDto.Quarter getLatestEstimate(String ticker, String quarterId)
    {
        Map.Entry<String, FirebaseCompany.FinnhubEarnings> latest = getLatestEarnings(ticker, quarterId);
        if (latest == null) return null;

        EstimateImportDto.Quarter quarter = new EstimateImportDto.Quarter();
        quarter.setEps(firstNonBlank(latest.getValue().getEpsa(), latest.getValue().getEpse()));
        quarter.setDate(snapshotDate(latest.getKey()));
        return quarter;
    }

    public String getLatestActualEps(String ticker, String quarterId)
    {
        Map.Entry<String, FirebaseCompany.FinnhubEarnings> latest = getLatestEarnings(ticker, quarterId);
        if (latest == null) return null;

        String actualEps = latest.getValue().getEpsa();
        return actualEps == null || actualEps.isBlank() ? null : actualEps;
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
        quarter.setReported_capex(toString(period.getCapex()));
        quarter.setReported_free_cash_flow(toString(period.getFreeCashFlow()));

        firebaseStore.updateQuarter(ticker, quarterId, quarter);
    }

    public void deleteTarget(String ticker, LocalDate date, String institution, BigDecimal price)
    {
        try {
            String normalizedInstitution = institution.trim().toLowerCase(Locale.ROOT);
            BigDecimal normalizedPrice = price.stripTrailingZeros();

            for (Map.Entry<String, FirebaseCompany.Gemini.Target> entry
                    : firebaseStore.findTargets(ticker).entrySet()) {
                FirebaseCompany.Gemini.Target target = entry.getValue();
                if (!matches(target, date, normalizedInstitution, normalizedPrice)) continue;

                firebaseStore.deleteTarget(ticker, entry.getKey());
                return;
            }
        } catch (RuntimeException exception) {
            String warning = ExternalWarnings.unavailable("Firebase target delete for " + ticker, exception);
            Log.warn(warning, exception);
        }
    }

    private boolean matches(
            FirebaseCompany.Gemini.Target target,
            LocalDate date,
            String normalizedInstitution,
            BigDecimal normalizedPrice)
    {
        if (target == null || target.getDate() == null
                || target.getInstitution() == null || target.getPrice() == null) {
            return false;
        }

        LocalDate targetDate;
        BigDecimal targetPrice;
        try {
            targetDate = LocalDate.parse(target.getDate());
            targetPrice = new BigDecimal(target.getPrice()).stripTrailingZeros();
        } catch (RuntimeException exception) {
            return false;
        }

        return date.equals(targetDate)
                && normalizedInstitution.equals(target.getInstitution().trim().toLowerCase(Locale.ROOT))
                && normalizedPrice.equals(targetPrice);
    }

    private Map.Entry<String, FirebaseCompany.FinnhubEarnings> getLatestEarnings(
            String ticker,
            String quarterId)
    {
        Map<String, FirebaseCompany.FinnhubEarnings> estimates = firebaseStore.findEarnings(ticker, quarterId);
        if (estimates == null || estimates.isEmpty()) return null;

        return estimates.entrySet().stream()
                .max(Map.Entry.comparingByKey())
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

    private String snapshotDate(String key)
    {
        if (key == null) return null;
        try {
            return LocalDate.parse(key, DateTimeFormatter.BASIC_ISO_DATE).toString();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
