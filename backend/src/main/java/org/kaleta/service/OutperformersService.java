package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.model.Periods;
import org.kaleta.persistence.api.LatestDao;
import org.kaleta.persistence.api.TargetDao;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.persistence.entity.PeriodType;
import org.kaleta.persistence.entity.Target;
import org.kaleta.rest.dto.DisqualifiedCompanyDto;
import org.kaleta.rest.dto.EpsOutperformerDto;
import org.kaleta.rest.dto.MarginOutperformerDto;
import org.kaleta.rest.dto.OutperformersDto;
import org.kaleta.rest.dto.SentimentOutperformerDto;
import org.kaleta.rest.dto.TargetOutperformerDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
public class OutperformersService
{
    private static final int MIN_SENTIMENT_ARTICLES = 5;
    private static final int MIN_TARGETS = 5;
    private static final int SENTIMENT_WINDOW_DAYS = 31;
    private static final int TARGET_WINDOW_MONTHS = 3;
    private static final int ESTIMATE_MAX_AGE_MONTHS = 3;
    private static final int FINANCIAL_MAX_AGE_MONTHS = 3;
    private static final int PRICE_MAX_AGE_DAYS = 7;
    private static final int MIN_MARGIN_QUARTERS = 4;
    private static final Set<PeriodType> QUARTER_TYPES = Set.of(
            PeriodType.Q1, PeriodType.Q2, PeriodType.Q3, PeriodType.Q4);
    private static final Map<String, Integer> SENTIMENT_WEIGHTS = Map.of(
            "positive", 2,
            "neutral", 1);

    @Inject
    CompanyService companyService;
    @Inject
    PeriodService periodService;
    @Inject
    EstimateService estimateService;
    @Inject
    TargetDao targetDao;
    @Inject
    LatestDao latestDao;
    @Inject
    FirebaseService firebaseService;
    @Inject
    ArithmeticService arithmeticService;

    public OutperformersDto get()
    {
        OutperformersDto dto = new OutperformersDto();
        List<CompanyWithStats> companies = companyService.getAllWithStats();
        FirebaseService.AllCompaniesResult firebaseData = firebaseService.getAllCompanies();
        LocalDate today = LocalDate.now();

        for (CompanyWithStats company : companies) {
            Periods periods = periodService.getBy(company.getId());
            addEps(dto, company, periods, today);
            addMargins(dto, company, periods, today);
            addSentiment(dto, company, firebaseData.companies().get(company.getTicker()), today);
            addTargets(dto, company, periods, today);
        }

        dto.getEpsEstimates().sort(Comparator.comparing(
                EpsOutperformerDto::getQuarter4Change, Comparator.nullsLast(Comparator.reverseOrder())));
        dto.getMargins().sort(Comparator.comparing(
                MarginOutperformerDto::getGrossMargin, Comparator.nullsLast(Comparator.reverseOrder())));
        dto.getSentiment().sort(Comparator.comparing(
                SentimentOutperformerDto::getSentimentScore, Comparator.reverseOrder()));
        dto.getTargets().sort(Comparator.comparing(
                TargetOutperformerDto::getPercentDiff, Comparator.nullsLast(Comparator.reverseOrder())));

        return dto;
    }

    private void addEps(OutperformersDto dto, CompanyWithStats company, Periods periods, LocalDate today)
    {
        Periods.Period latestPeriod = periods.getPeriods().stream().findFirst().orElse(null);
        if (latestPeriod == null) return;

        PeriodEstimates estimates = estimateService.getLatest(latestPeriod.getId(), Estimate.EPS).orElse(null);
        if (estimates == null) return;

        if (estimates.getDatetime() != null
                && estimates.getDatetime().toLocalDate().isBefore(today.minusMonths(ESTIMATE_MAX_AGE_MONTHS))) {
            disqualify(dto.getEpsEstimatesDisqualified(), company,
                    "estimate data is stale (last updated " + estimates.getDatetime().toLocalDate() + ")");
            return;
        }

        BigDecimal quarter1Change = cumulativeChange(estimates, 1);
        BigDecimal quarter2Change = cumulativeChange(estimates, 2);
        BigDecimal quarter3Change = cumulativeChange(estimates, 3);
        BigDecimal quarter4Change = cumulativeChange(estimates, 4);
        if (quarter1Change == null && quarter2Change == null && quarter3Change == null && quarter4Change == null) {
            disqualify(dto.getEpsEstimatesDisqualified(), company, "not enough estimate history to compute a change");
            return;
        }

        EpsOutperformerDto entry = new EpsOutperformerDto();
        entry.setTicker(company.getTicker());
        entry.setTtmEps(baselineSum(estimates));
        entry.setQuarter1Change(quarter1Change);
        entry.setQuarter2Change(quarter2Change);
        entry.setQuarter3Change(quarter3Change);
        entry.setQuarter4Change(quarter4Change);
        dto.getEpsEstimates().add(entry);
    }

    private BigDecimal baselineSum(PeriodEstimates estimates)
    {
        List<BigDecimal> baseline = Arrays.asList(
                estimates.getPast4(), estimates.getPast3(), estimates.getPast2(), estimates.getPast1());
        if (baseline.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        return baseline.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal cumulativeChange(PeriodEstimates estimates, int quartersForward)
    {
        List<BigDecimal> values = Arrays.asList(
                estimates.getPast4(), estimates.getPast3(), estimates.getPast2(), estimates.getPast1(),
                estimates.getCurrent(), estimates.getNext1(), estimates.getNext2(), estimates.getNext3());
        BigDecimal baselineSum = baselineSum(estimates);
        List<BigDecimal> future = values.subList(quartersForward, quartersForward + 4);
        if (baselineSum == null || future.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        BigDecimal futureSum = future.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return arithmeticService.profitPercentage(baselineSum, futureSum);
    }

    private void addMargins(OutperformersDto dto, CompanyWithStats company, Periods periods, LocalDate today)
    {
        Periods.Period latestReported = periods.getPeriods().stream()
                .filter(period -> period.getFinancial() != null)
                .findFirst()
                .orElse(null);
        if (latestReported == null || periods.getTtm() == null) return;

        if (latestReported.getReportDate() != null
                && latestReported.getReportDate().toLocalDate().isBefore(today.minusMonths(FINANCIAL_MAX_AGE_MONTHS))) {
            disqualify(dto.getMarginsDisqualified(), company,
                    "financial data is stale (reported " + latestReported.getReportDate().toLocalDate() + ")");
            return;
        }

        List<Periods.Period> reportedQuarters = periods.getPeriods().stream()
                .filter(period -> period.getFinancial() != null)
                .filter(period -> QUARTER_TYPES.contains(period.getName().getType()))
                .toList();
        List<Periods.Period> consecutiveQuarters = latestConsecutiveQuarters(reportedQuarters);
        if (consecutiveQuarters.size() < MIN_MARGIN_QUARTERS) {
            disqualify(dto.getMarginsDisqualified(), company,
                    "fewer than " + MIN_MARGIN_QUARTERS + " consecutive quarterly reports available ("
                            + consecutiveQuarters.size() + ")");
            return;
        }

        MarginOutperformerDto entry = new MarginOutperformerDto();
        entry.setTicker(company.getTicker());
        entry.setRevenue(periods.getTtm().getRevenue().getValue());
        entry.setGrossMargin(periods.getTtm().getGrossProfit().getMargin());
        entry.setOperatingMargin(periods.getTtm().getOperatingIncome().getMargin());
        entry.setNetMargin(periods.getTtm().getNetIncome().getMargin());
        dto.getMargins().add(entry);
    }

    private List<Periods.Period> latestConsecutiveQuarters(List<Periods.Period> quartersNewestFirst)
    {
        List<Periods.Period> result = new ArrayList<>();
        Integer previousIndex = null;
        for (Periods.Period period : quartersNewestFirst) {
            int index = quarterIndex(period.getName());
            if (previousIndex != null && previousIndex - index != 1) break;
            result.add(period);
            previousIndex = index;
        }
        return result;
    }

    private int quarterIndex(PeriodName name)
    {
        return name.getYear().getValue() * 4 + (name.getType().getNumber() - 1);
    }

    private void addSentiment(
            OutperformersDto dto,
            CompanyWithStats company,
            FirebaseCompany firebaseCompany,
            LocalDate today)
    {
        if (firebaseCompany == null || firebaseCompany.getPgn() == null || firebaseCompany.getPgn().isEmpty()) return;

        LocalDate windowStart = today.minusDays(SENTIMENT_WINDOW_DAYS);
        int articleCount = 0;
        int positiveCount = 0;
        int neutralCount = 0;
        int negativeCount = 0;
        int weightedSentiment = 0;
        for (Map.Entry<String, FirebaseCompany.NewsSentiment> entry : firebaseCompany.getPgn().entrySet()) {
            LocalDate date = parseSentimentDate(entry.getKey());
            if (date == null || date.isBefore(windowStart) || date.isAfter(today)) continue;
            Map<String, Integer> stats = entry.getValue() == null ? null : entry.getValue().getSentiment();
            if (stats == null) continue;
            for (Map.Entry<String, Integer> stat : stats.entrySet()) {
                if (stat.getValue() == null) continue;
                int count = stat.getValue();
                articleCount += count;
                weightedSentiment += SENTIMENT_WEIGHTS.getOrDefault(stat.getKey(), 0) * count;
                switch (stat.getKey()) {
                    case "positive" -> positiveCount += count;
                    case "neutral" -> neutralCount += count;
                    case "negative" -> negativeCount += count;
                    default -> { }
                }
            }
        }

        if (articleCount < MIN_SENTIMENT_ARTICLES) {
            disqualify(dto.getSentimentDisqualified(), company,
                    "only " + articleCount + " articles in the last " + SENTIMENT_WINDOW_DAYS
                            + " days (need " + MIN_SENTIMENT_ARTICLES + ")");
            return;
        }

        SentimentOutperformerDto entry = new SentimentOutperformerDto();
        entry.setTicker(company.getTicker());
        entry.setArticleCount(articleCount);
        entry.setPositiveCount(positiveCount);
        entry.setNeutralCount(neutralCount);
        entry.setNegativeCount(negativeCount);
        entry.setWeightedSentimentSum(weightedSentiment);
        entry.setSentimentScore(BigDecimal.valueOf(weightedSentiment)
                .divide(BigDecimal.valueOf(Math.sqrt(articleCount)), 2, RoundingMode.HALF_UP));
        dto.getSentiment().add(entry);
    }

    private LocalDate parseSentimentDate(String key)
    {
        if (key == null || key.length() < 10) return null;
        try {
            return LocalDate.parse(key.substring(0, 10));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private void addTargets(OutperformersDto dto, CompanyWithStats company, Periods periods, LocalDate today)
    {
        List<Long> periodIds = periods.getPeriods().stream().map(Periods.Period::getId).toList();
        if (periodIds.isEmpty()) return;

        List<Target> allTargets = targetDao.listByPeriodIds(periodIds);
        if (allTargets.isEmpty()) return;

        LocalDate windowStart = today.minusMonths(TARGET_WINDOW_MONTHS);
        List<Target> recentTargets = allTargets.stream()
                .filter(target -> !target.getDate().toLocalDate().isBefore(windowStart))
                .toList();

        if (recentTargets.size() < MIN_TARGETS) {
            disqualify(dto.getTargetsDisqualified(), company,
                    "only " + recentTargets.size() + " targets in the last " + TARGET_WINDOW_MONTHS
                            + " months (need " + MIN_TARGETS + ")");
            return;
        }

        Latest latest = latestDao.list(company.getId()).stream().findFirst().orElse(null);
        if (latest == null || latest.getPrice() == null) {
            disqualify(dto.getTargetsDisqualified(), company, "no cached price available");
            return;
        }
        if (latest.getDatetime() != null
                && latest.getDatetime().toLocalDate().isBefore(today.minusDays(PRICE_MAX_AGE_DAYS))) {
            disqualify(dto.getTargetsDisqualified(), company,
                    "cached price is stale (last updated " + latest.getDatetime().toLocalDate() + ")");
            return;
        }

        BigDecimal averageTarget = recentTargets.stream()
                .map(Target::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recentTargets.size()), 4, RoundingMode.HALF_UP);

        TargetOutperformerDto entry = new TargetOutperformerDto();
        entry.setTicker(company.getTicker());
        entry.setPrice(latest.getPrice());
        entry.setAverageTarget(averageTarget);
        entry.setPercentDiff(arithmeticService.profitPercentage(latest.getPrice(), averageTarget));
        dto.getTargets().add(entry);
    }

    private void disqualify(List<DisqualifiedCompanyDto> list, CompanyWithStats company, String reason)
    {
        DisqualifiedCompanyDto entry = new DisqualifiedCompanyDto();
        entry.setTicker(company.getTicker());
        entry.setReason(reason);
        list.add(entry);
    }
}
