package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;
import org.kaleta.client.dto.FinnhubQuote;
import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonPriceRange;

import java.math.BigDecimal;
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
        Optional<PolygonPriceRange> priceRange = client.getPriceRange(
                "intc",
                "2026-01-22",
                "2026-04-23");

        assertThat(financials.isPresent(), is(true));
        assertThat(financials.orElseThrow().revenue(), comparesEqualTo(new BigDecimal("13577000000")));
        assertThat(financials.orElseThrow().netIncome(), comparesEqualTo(new BigDecimal("-3730000000")));
        assertThat(priceRange.isPresent(), is(true));
        assertThat(priceRange.orElseThrow().high(), comparesEqualTo(new BigDecimal("70.33")));
        assertThat(priceRange.orElseThrow().low(), comparesEqualTo(new BigDecimal("40.63")));
        assertThat(client.getFinancials("UNKNOWN", "2026", "Q1"), is(Optional.empty()));
        assertThat(client.getPriceRange("INTC", "2000-01-01", "2000-02-01"), is(Optional.empty()));
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
        assertThat(cashFlow.isPresent(), is(true));
        assertThat(cashFlow.orElseThrow().capex(), comparesEqualTo(new BigDecimal("180000000")));
        assertThat(cashFlow.orElseThrow().freeCashFlow(), comparesEqualTo(new BigDecimal("1650000000")));
        assertThat(client.getCashFlow("AMD", "26Q2", "2026-05"), is(Optional.empty()));
        assertThat(client.getIncomeStatement("UNKNOWN", "26Q2", "2026-06"), is(Optional.empty()));
    }
}
