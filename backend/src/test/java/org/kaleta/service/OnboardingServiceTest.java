package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.mockito.MockitoConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.client.AlphaVantageClient;
import org.kaleta.client.FinnhubClient;
import org.kaleta.client.GeminiClient;
import org.kaleta.client.PolygonClient;
import org.kaleta.client.RequestFailureException;
import org.kaleta.client.dto.AlphaVantageTicker;
import org.kaleta.client.dto.FinnhubEarnings;
import org.kaleta.client.dto.GeminiFinancials;
import org.kaleta.client.dto.GeminiTargets;
import org.kaleta.client.dto.PolygonCompanyProfile;
import org.kaleta.client.dto.PolygonNews;
import org.kaleta.firebase.FirebaseStore;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.model.FirebaseInstitution;
import org.kaleta.rest.dto.OnboardingLookupDto;
import org.kaleta.rest.dto.OnboardingEstimatesDto;
import org.kaleta.rest.dto.OnboardingFinancialsDto;
import org.kaleta.rest.dto.OnboardingNewsDto;
import org.kaleta.rest.dto.OnboardingTargetsDto;
import org.kaleta.rest.error.InvalidInputException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class OnboardingServiceTest
{
    @InjectMock
    CompanyService companyService;
    @InjectMock
    PolygonClient polygonClient;
    @InjectMock
    AlphaVantageClient alphaVantageClient;
    @InjectMock
    @MockitoConfig(convertScopes = true)
    FirebaseStore firebaseStore;
    @InjectMock
    GeminiClient geminiClient;
    @InjectMock
    FinnhubClient finnhubClient;

    @Inject
    OnboardingService onboardingService;

    @Test
    void lookup_newCompany() throws Exception
    {
        arrangeDatabase("AMD");
        when(firebaseStore.findCompany("ORCL")).thenReturn(Optional.empty());
        when(polygonClient.getCompanyProfile("ORCL")).thenReturn(Optional.of(profile()));

        OnboardingLookupDto result = onboardingService.lookup(" orcl ");

        assertThat(result.ticker(), is("ORCL"));
        assertThat(result.found(), is(true));
        assertThat(result.inDatabase(), is(false));
        assertThat(result.inFirebase(), is(false));
        assertThat(result.name(), is("Oracle Corporation"));
        assertThat(result.description(), is("Cloud and database company."));
        assertThat(result.website(), is("https://oracle.com"));
        assertThat(result.exchange(), is("XNYS"));
        assertThat(result.currency(), is("usd"));
        assertThat(result.locale(), is("us"));
        assertThat(result.industry(), is("SERVICES-PREPACKAGED SOFTWARE"));
        assertThat(result.marketCap(), is(new BigDecimal("655000000000")));
        assertThat(result.sharesOutstanding(), is(new BigDecimal("2800000000")));
        assertThat(result.employees(), is(159000));
        assertThat(result.listDate(), is("1986-03-12"));
        assertThat(result.warnings(), is(empty()));
        verify(alphaVantageClient, never()).searchTickers(any());
    }

    @Test
    void lookup_companyInDatabaseSkipsExternalSources() throws Exception
    {
        arrangeDatabase("AMD", "NVDA");
        when(firebaseStore.findCompany("NVDA")).thenReturn(Optional.empty());

        OnboardingLookupDto result = onboardingService.lookup("nvda");

        assertThat(result.found(), is(false));
        assertThat(result.inDatabase(), is(true));
        assertThat(result.inFirebase(), is(false));
        assertThat(result.name(), is(nullValue()));
        verify(polygonClient, never()).getCompanyProfile(any());
    }

    @Test
    void lookup_companyInFirebaseSkipsExternalSources() throws Exception
    {
        arrangeDatabase("AMD");
        when(firebaseStore.findCompany("ORCL")).thenReturn(Optional.of(new FirebaseCompany()));

        OnboardingLookupDto result = onboardingService.lookup("ORCL");

        assertThat(result.found(), is(false));
        assertThat(result.inDatabase(), is(false));
        assertThat(result.inFirebase(), is(true));
        verify(polygonClient, never()).getCompanyProfile(any());
    }

    @Test
    void lookup_unknownTicker() throws Exception
    {
        arrangeDatabase();
        when(firebaseStore.findCompany("XYZQ")).thenReturn(Optional.empty());
        when(polygonClient.getCompanyProfile("XYZQ")).thenReturn(Optional.empty());

        OnboardingLookupDto result = onboardingService.lookup("XYZQ");

        assertThat(result.found(), is(false));
        assertThat(result.name(), is(nullValue()));
        assertThat(result.warnings(), is(empty()));
    }

    @Test
    void lookup_reportsFailingSourcesAsWarnings() throws Exception
    {
        arrangeDatabase();
        when(firebaseStore.findCompany("ORCL")).thenThrow(new IllegalStateException("firebase down"));
        when(polygonClient.getCompanyProfile("ORCL")).thenThrow(new RequestFailureException("polygon down"));

        OnboardingLookupDto result = onboardingService.lookup("ORCL");

        assertThat(result.found(), is(false));
        assertThat(result.inFirebase(), is(false));
        assertThat(result.warnings(), hasSize(2));
        assertThat(result.warnings().get(0), containsString("firebase down"));
        assertThat(result.warnings().get(1), containsString("polygon down"));
    }

    @Test
    void targets_returnsSortedTargetsWithStatsAndReport() throws Exception
    {
        when(firebaseStore.findAllInstitutions()).thenReturn(Map.of(
                "morgan-stanley", institution("Morgan Stanley", true, true),
                "ubs", institution("UBS", true, true),
                "small-shop", institution("Small Shop", false, true),
                "retired-bank", institution("Retired Bank", true, false)));
        when(geminiClient.getCompanyTargets(eq("CRM"), eq(List.of("Morgan Stanley", "UBS")), any(), any()))
                .thenReturn(new GeminiTargets(
                        List.of(
                                new GeminiTargets.Target("UBS", "2026-08-24", new BigDecimal("285"),
                                        "Neutral", "https://ubs.com", "priced in", List.of("valuation")),
                                new GeminiTargets.Target("Morgan Stanley", "2026-09-09", new BigDecimal("320"),
                                        "Overweight", "https://ms.com", "agentic seats", List.of("data cloud")),
                                new GeminiTargets.Target("Noise", "2026-09-01", null,
                                        null, "https://noise.com", null, null)),
                        new GeminiTargets.Report("merged view", List.of("agreement", "disagreement"))));

        OnboardingTargetsDto result = onboardingService.targets("crm");

        assertThat(result.ticker(), is("CRM"));
        assertThat(result.institutions(), contains("Morgan Stanley", "UBS"));
        assertThat(result.targets(), hasSize(2));
        assertThat(result.targets().get(0).institution(), is("Morgan Stanley"));
        assertThat(result.targets().get(1).institution(), is("UBS"));
        assertThat(result.stats().count(), is(2));
        assertThat(result.stats().minimum(), is(new BigDecimal("285")));
        assertThat(result.stats().maximum(), is(new BigDecimal("320")));
        assertThat(result.stats().average(), is(new BigDecimal("302.50")));
        assertThat(result.report().overview(), is("merged view"));
        assertThat(result.report().keyTakeaways(), contains("agreement", "disagreement"));
        assertThat(result.from(), is(LocalDate.now().minusMonths(1).toString()));
        assertThat(result.to(), is(LocalDate.now().toString()));
        assertThat(result.warnings(), is(empty()));
    }

    @Test
    void targets_withoutAnyResult() throws Exception
    {
        when(firebaseStore.findAllInstitutions()).thenReturn(Map.of(
                "ubs", institution("UBS", true, true)));
        when(geminiClient.getCompanyTargets(eq("CRM"), eq(List.of("UBS")), any(), any()))
                .thenReturn(new GeminiTargets(List.of(), new GeminiTargets.Report("nothing published", List.of())));

        OnboardingTargetsDto result = onboardingService.targets("CRM");

        assertThat(result.targets(), is(empty()));
        assertThat(result.stats().count(), is(0));
        assertThat(result.stats().average(), is(nullValue()));
        assertThat(result.report().overview(), is("nothing published"));
    }

    @Test
    void targets_withoutTrustedInstitutionsSkipsGemini() throws Exception
    {
        when(firebaseStore.findAllInstitutions()).thenReturn(Map.of(
                "small-shop", institution("Small Shop", false, true)));

        OnboardingTargetsDto result = onboardingService.targets("CRM");

        assertThat(result.institutions(), is(empty()));
        assertThat(result.targets(), is(empty()));
        assertThat(result.report(), is(nullValue()));
        assertThat(result.warnings(), contains("No trusted institution is configured in Firebase."));
        verify(geminiClient, never()).getCompanyTargets(any(), any(), any(), any());
    }

    @Test
    void targets_failingGeminiIsReportedAsInvalidInput() throws Exception
    {
        when(firebaseStore.findAllInstitutions()).thenReturn(Map.of(
                "ubs", institution("UBS", true, true)));
        when(geminiClient.getCompanyTargets(any(), any(), any(), any()))
                .thenThrow(new RequestFailureException("quota exceeded"));

        InvalidInputException exception = assertThrows(
                InvalidInputException.class, () -> onboardingService.targets("CRM"));

        assertThat(exception.getMessage(), containsString("quota exceeded"));
    }

    @Test
    void financials_returnsRequestedQuarters() throws Exception
    {
        when(geminiClient.getCompanyFinancials("CRM", 4)).thenReturn(new GeminiFinancials(
                List.of(
                        quarter("27Q2", new BigDecimal("10240"), new BigDecimal("1.81")),
                        quarter("27Q1", new BigDecimal("9870"), new BigDecimal("1.62")),
                        quarter("26Q4", new BigDecimal("9610"), new BigDecimal("1.54")),
                        quarter("26Q3", new BigDecimal("9240"), new BigDecimal("1.42"))),
                List.of("dividend of 26Q3 is unverified")));

        OnboardingFinancialsDto result = onboardingService.financials("crm");

        assertThat(result.ticker(), is("CRM"));
        assertThat(result.quarters(), hasSize(4));
        assertThat(result.quarters().get(0).id(), is("27Q2"));
        assertThat(result.quarters().get(0).revenue(), is(new BigDecimal("10240")));
        assertThat(result.quarters().get(0).adjustedEps(), is(new BigDecimal("1.81")));
        assertThat(result.notes(), contains("dividend of 26Q3 is unverified"));
        assertThat(result.warnings(), is(empty()));
    }

    @Test
    void financials_warnsAboutMissingQuarters() throws Exception
    {
        when(geminiClient.getCompanyFinancials("CRM", 4)).thenReturn(new GeminiFinancials(
                List.of(quarter("27Q2", new BigDecimal("10240"), new BigDecimal("1.81"))),
                List.of()));

        OnboardingFinancialsDto result = onboardingService.financials("CRM");

        assertThat(result.quarters(), hasSize(1));
        assertThat(result.warnings(), contains("Gemini returned 1 of 4 requested quarters."));
    }

    @Test
    void financials_failingGeminiIsReportedAsInvalidInput() throws Exception
    {
        when(geminiClient.getCompanyFinancials(any(), anyInt()))
                .thenThrow(new RequestFailureException("model overloaded"));

        InvalidInputException exception = assertThrows(
                InvalidInputException.class, () -> onboardingService.financials("CRM"));

        assertThat(exception.getMessage(), containsString("model overloaded"));
    }

    @Test
    void estimates_projectsRollingEpsWindows() throws Exception
    {
        LocalDate today = LocalDate.now();
        when(finnhubClient.earningsCalendar(eq("CRM"), any(), any())).thenReturn(List.of(
                earnings(today.minusMonths(13), 2026, 3, new BigDecimal("1.00"), null),
                earnings(today.minusMonths(10), 2026, 4, new BigDecimal("1.00"), null),
                earnings(today.minusMonths(7), 2027, 1, new BigDecimal("1.00"), null),
                earnings(today.minusMonths(4), 2027, 2, new BigDecimal("1.00"), null),
                earnings(today.plusMonths(2), 2027, 3, null, new BigDecimal("2.00")),
                earnings(today.plusMonths(5), 2027, 4, null, new BigDecimal("2.00")),
                earnings(today.plusMonths(8), 2028, 1, null, new BigDecimal("2.00")),
                earnings(today.plusMonths(11), 2028, 2, null, new BigDecimal("2.00"))));

        OnboardingEstimatesDto result = onboardingService.estimates("crm");

        assertThat(result.ticker(), is("CRM"));
        assertThat(result.reported(), hasSize(4));
        assertThat(result.reported().get(0).label(), is("26Q3"));
        assertThat(result.estimated(), hasSize(4));
        assertThat(result.estimated().get(0).label(), is("27Q3"));
        assertThat(result.projection().ttm().eps(), is(new BigDecimal("4.00")));
        assertThat(result.projection().current().eps(), is(new BigDecimal("5.00")));
        assertThat(result.projection().current().change().doubleValue(), is(25.0));
        assertThat(result.projection().next3().eps(), is(new BigDecimal("8.00")));
        assertThat(result.projection().next3().change().doubleValue(), is(100.0));
        assertThat(result.warnings(), is(empty()));
    }

    @Test
    void estimates_withoutFourReportedQuartersHasNoProjection() throws Exception
    {
        LocalDate today = LocalDate.now();
        when(finnhubClient.earningsCalendar(eq("CRM"), any(), any())).thenReturn(List.of(
                earnings(today.minusMonths(4), 2027, 2, new BigDecimal("1.00"), null),
                earnings(today.plusMonths(2), 2027, 3, null, new BigDecimal("2.00"))));

        OnboardingEstimatesDto result = onboardingService.estimates("CRM");

        assertThat(result.reported(), hasSize(1));
        assertThat(result.estimated(), hasSize(1));
        assertThat(result.projection(), is(nullValue()));
        assertThat(result.warnings(), hasSize(2));
        assertThat(result.warnings().get(0), containsString("only 1 of 4 past quarters"));
        assertThat(result.warnings().get(1), containsString("only 1 of 4 upcoming quarters"));
    }

    @Test
    void estimates_failingFinnhubIsReportedAsInvalidInput() throws Exception
    {
        when(finnhubClient.earningsCalendar(any(), any(), any()))
                .thenThrow(new RequestFailureException("api limit reached"));

        InvalidInputException exception = assertThrows(
                InvalidInputException.class, () -> onboardingService.estimates("CRM"));

        assertThat(exception.getMessage(), containsString("api limit reached"));
    }

    @Test
    void news_countsSentimentOfTheRequestedTicker() throws Exception
    {
        when(polygonClient.getNews(eq("CRM"), any(), anyInt())).thenReturn(List.of(
                news("Agentforce expands", "positive", "broader base"),
                news("Pricing debate", "neutral", "depends on renewals"),
                news("Deal lost to rival", "negative", "competitive loss"),
                news("Usage milestone", "positive", "supports the thesis"),
                new PolygonNews("5", "No insight for this ticker", "Reuters",
                        "2026-09-01T10:00:00Z", "https://example.test/5", null,
                        List.of(new PolygonNews.Insight("MSFT", "positive", "other company")))));

        OnboardingNewsDto result = onboardingService.news("crm");

        assertThat(result.ticker(), is("CRM"));
        assertThat(result.from(), is(LocalDate.now().minusMonths(1).toString()));
        assertThat(result.articles(), hasSize(5));
        assertThat(result.sentiment().total(), is(5));
        assertThat(result.sentiment().positive(), is(2));
        assertThat(result.sentiment().neutral(), is(1));
        assertThat(result.sentiment().negative(), is(1));
        assertThat(result.sentiment().unrated(), is(1));
        assertThat(result.articles().get(0).sentiment(), is("positive"));
        assertThat(result.articles().get(0).reasoning(), is("broader base"));
        assertThat(result.articles().get(4).sentiment(), is(nullValue()));
        assertThat(result.warnings(), is(empty()));
    }

    @Test
    void news_warnsWhenNothingWasPublished() throws Exception
    {
        when(polygonClient.getNews(eq("CRM"), any(), anyInt())).thenReturn(List.of());

        OnboardingNewsDto result = onboardingService.news("CRM");

        assertThat(result.articles(), is(empty()));
        assertThat(result.sentiment().total(), is(0));
        assertThat(result.warnings(), contains("Polygon returned no article for the last month."));
    }

    @Test
    void news_failingPolygonIsReportedAsInvalidInput() throws Exception
    {
        when(polygonClient.getNews(any(), any(), anyInt()))
                .thenThrow(new RequestFailureException("news endpoint unavailable"));

        InvalidInputException exception = assertThrows(
                InvalidInputException.class, () -> onboardingService.news("CRM"));

        assertThat(exception.getMessage(), containsString("news endpoint unavailable"));
    }

    private static PolygonNews news(String title, String sentiment, String reasoning)
    {
        return new PolygonNews(
                title, title, "Reuters", "2026-09-10T10:00:00Z",
                "https://example.test/" + title, "description",
                List.of(new PolygonNews.Insight("CRM", sentiment, reasoning)));
    }

    private static FinnhubEarnings earnings(
            LocalDate date, int year, int quarter, BigDecimal actual, BigDecimal estimate)
    {
        return new FinnhubEarnings(
                date.toString(), year, quarter, "amc", actual, estimate, null, null);
    }

    private static GeminiFinancials.Quarter quarter(String id, BigDecimal revenue, BigDecimal eps)
    {
        return new GeminiFinancials.Quarter(
                id, "Quarter " + id, "2026-07", "2026-08-27",
                revenue, null, null, null, null, null, null, null, eps, null, null);
    }

    @Test
    void pushToFirebase_createsTheCompanyKey() throws Exception
    {
        arrangeDatabase("AMD");
        when(firebaseStore.findCompany("CRM")).thenReturn(Optional.empty());

        onboardingService.pushToFirebase(" crm ");

        verify(firebaseStore).createCompany("CRM");
    }

    @Test
    void pushToFirebase_refusesKnownCompanies() throws Exception
    {
        arrangeDatabase("NVDA");
        when(firebaseStore.findCompany("ORCL")).thenReturn(Optional.of(new FirebaseCompany()));

        assertThat(assertThrows(InvalidInputException.class,
                        () -> onboardingService.pushToFirebase("NVDA")).getMessage(),
                containsString("already exists in the database"));
        assertThat(assertThrows(InvalidInputException.class,
                        () -> onboardingService.pushToFirebase("ORCL")).getMessage(),
                containsString("already exists in Firebase"));

        verify(firebaseStore, never()).createCompany(any());
    }

    @Test
    void pushToFirebase_refusesWhenFirebaseCannotBeRead() throws Exception
    {
        arrangeDatabase();
        when(firebaseStore.findCompany("CRM")).thenThrow(new IllegalStateException("firebase down"));

        assertThat(assertThrows(InvalidInputException.class,
                        () -> onboardingService.pushToFirebase("CRM")).getMessage(),
                containsString("firebase down"));

        verify(firebaseStore, never()).createCompany(any());
    }

    private static PolygonCompanyProfile profile()
    {
        return new PolygonCompanyProfile(
                "Oracle Corporation", "Cloud and database company.", "https://oracle.com",
                "XNYS", "usd", "us", "CS", true, "SERVICES-PREPACKAGED SOFTWARE", "1986-03-12",
                new BigDecimal("655000000000"), new BigDecimal("2800000000"), 159000);
    }

    private static FirebaseInstitution institution(String name, boolean trusted, boolean enabled)
    {
        FirebaseInstitution institution = new FirebaseInstitution();
        institution.setName(name);
        institution.setTrusted(trusted);
        institution.setEnabled(enabled);
        return institution;
    }

    private void arrangeDatabase(String... tickers)
    {
        when(companyService.getAllWithStats()).thenReturn(
                java.util.Arrays.stream(tickers).map(OnboardingServiceTest::company).toList());
    }

    private static CompanyWithStats company(String ticker)
    {
        CompanyWithStats company = new CompanyWithStats();
        company.setTicker(ticker);
        return company;
    }
}
