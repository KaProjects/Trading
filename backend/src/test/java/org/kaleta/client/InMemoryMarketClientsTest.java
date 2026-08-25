package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;
import org.kaleta.client.dto.AlphaVantageQuote;
import org.kaleta.client.dto.AlphaVantageTicker;
import org.kaleta.client.dto.FinnhubQuote;
import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonCompanyProfile;
import org.kaleta.client.dto.PolygonPriceRange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class InMemoryMarketClientsTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void polygonReadsFinancialsAndPriceRangesFromTestFixture()
    {
        InMemoryPolygonClient client = new InMemoryPolygonClient(
                objectMapper,
                "src/test/resources/polygonTestData.json");

        Optional<PolygonFinancials> financials = client.getFinancials("intc", "2026", "Q1");
        Optional<PolygonCompanyProfile> profile = client.getCompanyProfile("intc");
        Optional<PolygonPriceRange> priceRange = client.getPriceRange(
                "intc",
                "2026-01-22",
                "2026-04-23");

        assertThat(financials.isPresent(), is(true));
        assertThat(financials.orElseThrow().revenue(), comparesEqualTo(new BigDecimal("13577000000")));
        assertThat(financials.orElseThrow().netIncome(), comparesEqualTo(new BigDecimal("-3730000000")));
        assertThat(financials.orElseThrow().reportedCurrency(), is("USD"));
        assertThat(priceRange.isPresent(), is(true));
        assertThat(priceRange.orElseThrow().high(), comparesEqualTo(new BigDecimal("70.33")));
        assertThat(priceRange.orElseThrow().low(), comparesEqualTo(new BigDecimal("40.63")));
        assertThat(priceRange.orElseThrow().reportedCurrency(), is("USD"));
        assertThat(profile.orElseThrow().name(), is("Intel Corporation"));
        assertThat(profile.orElseThrow().website(), is("https://www.intel.com"));
        assertThat(client.getCompanyProfile("UNKNOWN"), is(Optional.empty()));
        assertThat(client.getFinancials("UNKNOWN", "2026", "Q1"), is(Optional.empty()));
        assertThat(client.getPriceRange("INTC", "2000-01-01", "2000-02-01"), is(Optional.empty()));
    }

    @Test
    void polygonDevFixtureProvidesCompanyProfiles()
    {
        InMemoryPolygonClient client = new InMemoryPolygonClient(
                objectMapper,
                "src/dev/resources/polygon.json");

        assertThat(client.getCompanyProfile("NVDA").orElseThrow().name(), is("NVIDIA Corporation"));
        assertThat(client.getCompanyProfile("AMD").orElseThrow().website(), is("https://www.amd.com"));
        assertThat(client.getCompanyProfile("INTC").orElseThrow().logoUrl(),
                is("https://upload.wikimedia.org/wikipedia/commons/thumb/6/6a/"
                        + "Intel_logo_%282020%2C_dark_blue%29.svg/"
                        + "330px-Intel_logo_%282020%2C_dark_blue%29.svg.png"));
        assertThat(client.getCompanyProfile("ASML"), is(Optional.empty()));
    }

    @Test
    void finnhubReadsQuotesFromTestFixture()
    {
        InMemoryFinnhubClient client = new InMemoryFinnhubClient(
                objectMapper,
                "src/test/resources/finnhubTestData.json");

        FinnhubQuote quote = client.quote("amd");

        assertThat(quote.getC(), is("158.25"));
        assertThat(quote.getT(), is("1784901600"));
        assertThat(client.quote("UNKNOWN"), is(nullValue()));
    }

    @Test
    void alphaVantageReadsStatementsFromTestFixture()
    {
        InMemoryAlphaVantageClient client = new InMemoryAlphaVantageClient(
                objectMapper,
                "src/test/resources/alphaVantageTestData.json");

        Optional<AlphaVantageIncomeStatement> income = client.getIncomeStatement(
                "amd", "26Q2", "2026-06");
        Optional<AlphaVantageCashFlow> cashFlow = client.getCashFlow(
                "amd", "26Q2", "2026-06");

        assertThat(income.isPresent(), is(true));
        assertThat(income.orElseThrow().revenue(), comparesEqualTo(new BigDecimal("11536000000")));
        assertThat(income.orElseThrow().netIncome(), comparesEqualTo(new BigDecimal("2297000000")));
        assertThat(income.orElseThrow().reportedCurrency(), is("USD"));
        assertThat(cashFlow.isPresent(), is(true));
        assertThat(cashFlow.orElseThrow().capex(), comparesEqualTo(new BigDecimal("180000000")));
        assertThat(cashFlow.orElseThrow().freeCashFlow(), comparesEqualTo(new BigDecimal("1650000000")));
        assertThat(cashFlow.orElseThrow().reportedCurrency(), is("USD"));
        assertThat(client.getCashFlow("AMD", "26Q2", "2026-05"), is(Optional.empty()));
        assertThat(client.getIncomeStatement("UNKNOWN", "26Q2", "2026-06"), is(Optional.empty()));
    }

    @Test
    void alphaVantageReadsTickerSearchAndQuoteFromTestFixture()
    {
        InMemoryAlphaVantageClient client = new InMemoryAlphaVantageClient(
                objectMapper,
                "src/test/resources/alphaVantageTestData.json");

        List<AlphaVantageTicker> tickers = client.searchTickers("asml");
        Optional<AlphaVantageQuote> quote = client.getQuote("asml.ams");
        var range = client.getPriceRange("asml.ams", "2026-04-22", "2026-07-22");
        var shares = client.getShares("asml.ams", "26Q2", "2026-06");
        var earnings = client.getEarnings("asml.ams", "26Q2", "2026-06");

        assertThat(tickers.size(), is(3));
        assertThat(tickers.get(1).symbol(), is("ASML.AMS"));
        assertThat(tickers.get(1).currency(), is("EUR"));
        assertThat(quote.isPresent(), is(true));
        assertThat(quote.orElseThrow().price(), comparesEqualTo(new BigDecimal("1489.8")));
        assertThat(quote.orElseThrow().date(), is(LocalDate.of(2026, 8, 24)));
        assertThat(range.orElseThrow().high(), comparesEqualTo(new BigDecimal("1550.4")));
        assertThat(range.orElseThrow().low(), comparesEqualTo(new BigDecimal("1178.2")));
        assertThat(shares.orElseThrow().shares(), comparesEqualTo(new BigDecimal("393000000")));
        assertThat(shares.orElseThrow().reportedCurrency(), is("EUR"));
        assertThat(earnings.orElseThrow().reportedEps(), comparesEqualTo(new BigDecimal("7.42")));
        assertThat(client.searchTickers("UNKNOWN"), is(List.of()));
        assertThat(client.getQuote("UNKNOWN"), is(Optional.empty()));
        assertThat(client.getPriceRange("UNKNOWN", "2026-04-22", "2026-07-22"), is(Optional.empty()));
        assertThat(client.getShares("UNKNOWN", "26Q2", "2026-06"), is(Optional.empty()));
        assertThat(client.getEarnings("UNKNOWN", "26Q2", "2026-06"), is(Optional.empty()));
    }

    @Test
    void alphaVantageDevFixtureSupportsAllNonUsdDevelopmentCompanies()
    {
        InMemoryAlphaVantageClient client = new InMemoryAlphaVantageClient(
                objectMapper,
                "src/dev/resources/alphavantage.json");

        assertDevelopmentCompany(client, "ASML", "ASML.AMS", "EUR", "26Q2", "2026-06",
                "2026-04-22", "2026-07-22");
        assertDevelopmentCompany(client, "AA", "AA.PAR", "EUR", "25H2", "2025-12",
                "2025-08-04", "2026-02-02");
        assertDevelopmentCompany(client, "CX", "CX.PRG", "CZK", "25FY", "2025-12",
                "2025-02-27", "2026-02-26");
        assertDevelopmentCompany(client, "EFG", "EFG.LON", "GBP", "26H1", "2026-06",
                "2026-02-10", "2026-08-10");
    }

    private void assertDevelopmentCompany(
            InMemoryAlphaVantageClient client,
            String searchTicker,
            String alphaVantageTicker,
            String currency,
            String periodName,
            String endingMonth,
            String priceRangeStart,
            String priceRangeEnd)
    {
        assertThat(client.searchTickers(searchTicker).stream().anyMatch(candidate ->
                candidate.symbol().equals(alphaVantageTicker)
                        && candidate.currency().equals(currency)), is(true));
        assertThat(client.getQuote(alphaVantageTicker).isPresent(), is(true));
        assertThat(client.getIncomeStatement(alphaVantageTicker, periodName, endingMonth)
                .orElseThrow().reportedCurrency(), is(currency));
        assertThat(client.getCashFlow(alphaVantageTicker, periodName, endingMonth)
                .orElseThrow().reportedCurrency(), is(currency));
        assertThat(client.getShares(alphaVantageTicker, periodName, endingMonth)
                .orElseThrow().reportedCurrency(), is(currency));
        assertThat(client.getEarnings(alphaVantageTicker, periodName, endingMonth).isPresent(), is(true));
        assertThat(client.getPriceRange(alphaVantageTicker, priceRangeStart, priceRangeEnd).isPresent(), is(true));
    }
}
