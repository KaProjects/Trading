package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.client.FinnhubClient;
import org.kaleta.client.GeminiClient;
import org.kaleta.client.PolygonClient;
import org.kaleta.client.RequestFailureException;
import org.kaleta.rest.error.InvalidInputException;
import org.kaleta.client.dto.FinnhubEarnings;
import org.kaleta.client.dto.GeminiFinancials;
import org.kaleta.client.dto.GeminiTargets;
import org.kaleta.client.dto.PolygonCompanyProfile;
import org.kaleta.client.dto.PolygonNews;
import org.kaleta.firebase.FirebaseStore;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.model.FirebaseInstitution;
import org.kaleta.rest.dto.OnboardingLookupDto;
import org.kaleta.rest.dto.OnboardingEstimatesDto;
import org.kaleta.rest.dto.OnboardingFinancialsDto;
import org.kaleta.rest.dto.OnboardingNewsDto;
import org.kaleta.rest.dto.OnboardingTargetsDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class OnboardingService
{
    private static final int TARGET_WINDOW_MONTHS = 1;
    private static final int FINANCIAL_QUARTERS = 4;
    private static final int ESTIMATE_QUARTERS = 4;
    private static final int EARNINGS_PAST_MONTHS = 15;
    private static final int EARNINGS_FUTURE_MONTHS = 12;
    private static final int NEWS_WINDOW_MONTHS = 1;
    private static final int NEWS_LIMIT = 100;

    @Inject
    CompanyService companyService;
    @Inject
    PolygonClient polygonClient;
    @Inject
    FirebaseStore firebaseStore;
    @Inject
    GeminiClient geminiClient;
    @Inject
    FinnhubClient finnhubClient;
    @Inject
    ArithmeticService arithmeticService;

    public OnboardingLookupDto lookup(String ticker)
    {
        String normalized = ticker.trim().toUpperCase(Locale.ROOT);
        List<String> warnings = new ArrayList<>();

        boolean inDatabase = databaseTickers().contains(normalized);
        boolean inFirebase = isInFirebase(normalized, warnings);
        if (inDatabase || inFirebase) {
            return new OnboardingLookupDto(
                    normalized, false, inDatabase, inFirebase,
                    null, null, null, null, null, null, null, null, null, null, null, null, null,
                    warnings);
        }

        PolygonCompanyProfile profile = loadProfile(normalized, warnings);

        return new OnboardingLookupDto(
                normalized,
                profile != null,
                false,
                false,
                profile == null ? null : profile.name(),
                profile == null ? null : profile.description(),
                profile == null ? null : profile.website(),
                profile == null ? null : profile.exchange(),
                profile == null ? null : profile.currency(),
                profile == null ? null : profile.locale(),
                profile == null ? null : profile.type(),
                profile == null ? null : profile.active(),
                profile == null ? null : profile.industry(),
                profile == null ? null : profile.listDate(),
                profile == null ? null : profile.marketCap(),
                profile == null ? null : profile.sharesOutstanding(),
                profile == null ? null : profile.employees(),
                warnings);
    }

    public OnboardingTargetsDto targets(String ticker)
    {
        String normalized = ticker.trim().toUpperCase(Locale.ROOT);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(TARGET_WINDOW_MONTHS);
        List<String> warnings = new ArrayList<>();
        List<String> institutions = trustedInstitutions(warnings);

        if (institutions.isEmpty()) {
            warnings.add("No trusted institution is configured in Firebase.");
            return new OnboardingTargetsDto(normalized, from.toString(), to.toString(),
                    institutions, List.of(), stats(List.of()), null, warnings);
        }

        GeminiTargets response;
        try {
            response = geminiClient.getCompanyTargets(normalized, institutions, from, to);
        } catch (RequestFailureException exception) {
            throw new InvalidInputException(
                    "Gemini price targets for '" + normalized + "' could not be loaded: "
                            + exception.getMessage());
        }

        List<OnboardingTargetsDto.Target> targets = response == null || response.targets() == null
                ? List.of()
                : response.targets().stream()
                        .filter(target -> target.price() != null)
                        .sorted(Comparator.comparing(
                                GeminiTargets.Target::date,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(target -> new OnboardingTargetsDto.Target(
                                target.institution(),
                                target.date(),
                                target.price(),
                                target.rating(),
                                target.source(),
                                target.overview(),
                                target.keyTakeaways() == null ? List.of() : target.keyTakeaways()))
                        .toList();

        GeminiTargets.Report report = response == null ? null : response.report();

        return new OnboardingTargetsDto(
                normalized,
                from.toString(),
                to.toString(),
                institutions,
                targets,
                stats(targets),
                report == null ? null : new OnboardingTargetsDto.Report(
                        report.overview(),
                        report.keyTakeaways() == null ? List.of() : report.keyTakeaways()),
                warnings);
    }

    public void pushToFirebase(String ticker)
    {
        String normalized = ticker.trim().toUpperCase(Locale.ROOT);

        if (databaseTickers().contains(normalized)) {
            throw new InvalidInputException(
                    "Company '" + normalized + "' already exists in the database");
        }

        List<String> warnings = new ArrayList<>();
        if (isInFirebase(normalized, warnings)) {
            throw new InvalidInputException(
                    "Company '" + normalized + "' already exists in Firebase");
        }
        if (!warnings.isEmpty()) {
            throw new InvalidInputException(warnings.getFirst());
        }

        firebaseStore.createCompany(normalized);
    }

    public OnboardingNewsDto news(String ticker)
    {
        String normalized = ticker.trim().toUpperCase(Locale.ROOT);
        LocalDate from = LocalDate.now().minusMonths(NEWS_WINDOW_MONTHS);

        List<PolygonNews> news;
        try {
            news = polygonClient.getNews(normalized, from.toString(), NEWS_LIMIT);
        } catch (RequestFailureException exception) {
            throw new InvalidInputException(
                    "Polygon news for '" + normalized + "' could not be loaded: "
                            + exception.getMessage());
        }

        List<OnboardingNewsDto.Article> articles = news.stream()
                .map(article -> new OnboardingNewsDto.Article(
                        article.title(),
                        article.publisher(),
                        article.publishedUtc(),
                        article.articleUrl(),
                        sentimentOf(article, normalized),
                        reasoningOf(article, normalized)))
                .toList();

        List<String> warnings = new ArrayList<>();
        if (articles.isEmpty()) {
            warnings.add("Polygon returned no article for the last month.");
        }

        return new OnboardingNewsDto(normalized, from.toString(), sentiment(articles), articles, warnings);
    }

    private OnboardingNewsDto.Sentiment sentiment(List<OnboardingNewsDto.Article> articles)
    {
        int positive = count(articles, "positive");
        int neutral = count(articles, "neutral");
        int negative = count(articles, "negative");

        return new OnboardingNewsDto.Sentiment(
                articles.size(),
                positive,
                neutral,
                negative,
                articles.size() - positive - neutral - negative);
    }

    private int count(List<OnboardingNewsDto.Article> articles, String sentiment)
    {
        return (int) articles.stream()
                .filter(article -> sentiment.equalsIgnoreCase(article.sentiment()))
                .count();
    }

    private String sentimentOf(PolygonNews article, String ticker)
    {
        return insightOf(article, ticker).map(PolygonNews.Insight::sentiment).orElse(null);
    }

    private String reasoningOf(PolygonNews article, String ticker)
    {
        return insightOf(article, ticker).map(PolygonNews.Insight::sentimentReasoning).orElse(null);
    }

    private Optional<PolygonNews.Insight> insightOf(PolygonNews article, String ticker)
    {
        if (article.insights() == null) return Optional.empty();

        return article.insights().stream()
                .filter(insight -> ticker.equalsIgnoreCase(insight.ticker()))
                .findFirst();
    }

    public OnboardingEstimatesDto estimates(String ticker)
    {
        String normalized = ticker.trim().toUpperCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        List<String> warnings = new ArrayList<>();

        List<FinnhubEarnings> earnings;
        try {
            earnings = finnhubClient.earningsCalendar(
                    normalized,
                    today.minusMonths(EARNINGS_PAST_MONTHS),
                    today.plusMonths(EARNINGS_FUTURE_MONTHS));
        } catch (RequestFailureException exception) {
            throw new InvalidInputException(
                    "Finnhub earnings for '" + normalized + "' could not be loaded: "
                            + exception.getMessage());
        }

        List<FinnhubEarnings> sorted = earnings.stream()
                .filter(entry -> entry.date() != null)
                .sorted(Comparator.comparing(FinnhubEarnings::date))
                .toList();

        List<OnboardingEstimatesDto.Quarter> reported = sorted.stream()
                .filter(entry -> entry.epsActual() != null)
                .map(entry -> quarter(entry, entry.epsActual(), entry.revenueActual()))
                .toList();
        reported = reported.size() <= ESTIMATE_QUARTERS
                ? reported
                : reported.subList(reported.size() - ESTIMATE_QUARTERS, reported.size());

        List<OnboardingEstimatesDto.Quarter> estimated = sorted.stream()
                .filter(entry -> entry.epsActual() == null && entry.epsEstimate() != null)
                .map(entry -> quarter(entry, entry.epsEstimate(), entry.revenueEstimate()))
                .limit(ESTIMATE_QUARTERS)
                .toList();

        if (reported.size() < ESTIMATE_QUARTERS) {
            warnings.add("Finnhub reported only " + reported.size() + " of " + ESTIMATE_QUARTERS
                    + " past quarters.");
        }
        if (estimated.size() < ESTIMATE_QUARTERS) {
            warnings.add("Finnhub estimates only " + estimated.size() + " of " + ESTIMATE_QUARTERS
                    + " upcoming quarters.");
        }

        return new OnboardingEstimatesDto(
                normalized, reported, estimated, projection(reported, estimated), warnings);
    }

    private OnboardingEstimatesDto.Quarter quarter(
            FinnhubEarnings entry,
            BigDecimal eps,
            BigDecimal revenue)
    {
        String label = entry.year() == null || entry.quarter() == null
                ? entry.date()
                : String.format("%02dQ%d", entry.year() % 100, entry.quarter());
        return new OnboardingEstimatesDto.Quarter(label, entry.date(), eps, revenue);
    }

    private OnboardingEstimatesDto.Projection projection(
            List<OnboardingEstimatesDto.Quarter> reported,
            List<OnboardingEstimatesDto.Quarter> estimated)
    {
        if (reported.size() < ESTIMATE_QUARTERS) return null;

        List<BigDecimal> values = new ArrayList<>();
        reported.forEach(quarter -> values.add(quarter.eps()));
        estimated.forEach(quarter -> values.add(quarter.eps()));

        List<BigDecimal> windows = new ArrayList<>();
        for (int offset = 0; offset + ESTIMATE_QUARTERS <= values.size(); offset++) {
            windows.add(sum(values.subList(offset, offset + ESTIMATE_QUARTERS)));
        }

        BigDecimal ttm = windows.getFirst();
        return new OnboardingEstimatesDto.Projection(
                new OnboardingEstimatesDto.Window(ttm, null),
                window(windows, 1, ttm),
                window(windows, 2, ttm),
                window(windows, 3, ttm),
                window(windows, 4, ttm));
    }

    private OnboardingEstimatesDto.Window window(List<BigDecimal> windows, int index, BigDecimal ttm)
    {
        if (index >= windows.size()) return null;

        BigDecimal value = windows.get(index);
        if (value == null) return null;

        return new OnboardingEstimatesDto.Window(
                value,
                ttm == null || ttm.signum() == 0 ? null : arithmeticService.profitPercentage(ttm, value));
    }

    private BigDecimal sum(List<BigDecimal> values)
    {
        if (values.stream().anyMatch(java.util.Objects::isNull)) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public OnboardingFinancialsDto financials(String ticker)
    {
        String normalized = ticker.trim().toUpperCase(Locale.ROOT);

        GeminiFinancials response;
        try {
            response = geminiClient.getCompanyFinancials(normalized, FINANCIAL_QUARTERS);
        } catch (RequestFailureException exception) {
            throw new InvalidInputException(
                    "Gemini financials for '" + normalized + "' could not be loaded: "
                            + exception.getMessage());
        }

        List<OnboardingFinancialsDto.Quarter> quarters = response == null || response.quarters() == null
                ? List.of()
                : response.quarters().stream()
                        .map(quarter -> new OnboardingFinancialsDto.Quarter(
                                quarter.id(),
                                quarter.name(),
                                quarter.endingMonth(),
                                quarter.reportDate(),
                                quarter.revenue(),
                                quarter.grossProfit(),
                                quarter.operatingIncome(),
                                quarter.netIncome(),
                                quarter.capex(),
                                quarter.freeCashFlow(),
                                quarter.dividend(),
                                quarter.shares(),
                                quarter.adjustedEps(),
                                quarter.priceMin(),
                                quarter.priceMax()))
                        .toList();

        List<String> warnings = new ArrayList<>();
        if (quarters.size() < FINANCIAL_QUARTERS) {
            warnings.add("Gemini returned " + quarters.size() + " of " + FINANCIAL_QUARTERS
                    + " requested quarters.");
        }

        return new OnboardingFinancialsDto(
                normalized,
                quarters,
                response == null || response.notes() == null ? List.of() : response.notes(),
                warnings);
    }

    private OnboardingTargetsDto.Stats stats(List<OnboardingTargetsDto.Target> targets)
    {
        if (targets.isEmpty()) return new OnboardingTargetsDto.Stats(0, null, null, null);

        List<BigDecimal> prices = targets.stream().map(OnboardingTargetsDto.Target::price).toList();
        BigDecimal total = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OnboardingTargetsDto.Stats(
                prices.size(),
                prices.stream().min(Comparator.naturalOrder()).orElseThrow(),
                prices.stream().max(Comparator.naturalOrder()).orElseThrow(),
                total.divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP));
    }

    private List<String> trustedInstitutions(List<String> warnings)
    {
        try {
            Map<String, FirebaseInstitution> institutions = firebaseStore.findAllInstitutions();
            return institutions.values().stream()
                    .filter(FirebaseInstitution::isTrusted)
                    .filter(FirebaseInstitution::isEnabled)
                    .map(FirebaseInstitution::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .sorted()
                    .toList();
        } catch (Exception exception) {
            warnings.add("Firebase institutions could not be loaded: " + exception.getMessage());
            return List.of();
        }
    }

    private PolygonCompanyProfile loadProfile(String ticker, List<String> warnings)
    {
        try {
            return polygonClient.getCompanyProfile(ticker).orElse(null);
        } catch (RequestFailureException exception) {
            warnings.add("Polygon company profile could not be loaded: " + exception.getMessage());
            return null;
        }
    }

    private boolean isInFirebase(String ticker, List<String> warnings)
    {
        try {
            Optional<?> company = firebaseStore.findCompany(ticker);
            return company.isPresent();
        } catch (Exception exception) {
            warnings.add("Firebase company data could not be loaded: " + exception.getMessage());
            return false;
        }
    }

    private Set<String> databaseTickers()
    {
        return companyService.getAllWithStats().stream()
                .map(CompanyWithStats::getTicker)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
