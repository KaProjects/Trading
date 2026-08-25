package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;
import org.kaleta.client.dto.AlphaVantageQuote;
import org.kaleta.client.dto.AlphaVantageTicker;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionAlphaVantageClientTest
{
    private HttpServer server;
    private String incomeResponse;
    private String cashFlowResponse;
    private String tickerSearchResponse;
    private String quoteResponse;
    private ProductionAlphaVantageClient client;

    @BeforeEach
    void before() throws IOException
    {
        incomeResponse = """
                {
                  "annualReports": [],
                  "quarterlyReports": [
                    {
                      "fiscalDateEnding": "2025-07-27",
                      "totalRevenue": "100000000",
                      "grossProfit": "60000000",
                      "operatingIncome": "30000000",
                      "netIncome": "20000000",
                      "reportedCurrency": "USD"
                    },
                    {
                      "fiscalDateEnding": "2025-04-27",
                      "totalRevenue": "90000000"
                    }
                  ]
                }
                """;
        cashFlowResponse = """
                {
                  "annualReports": [],
                  "quarterlyReports": [
                    {
                      "fiscalDateEnding": "2025-07-27",
                      "operatingCashflow": "40000000",
                      "capitalExpenditures": "-10000000",
                      "dividendPayout": "5000000",
                      "reportedCurrency": "USD"
                    }
                  ]
                }
                """;
        tickerSearchResponse = """
                {
                  "bestMatches": [
                    {
                      "1. symbol": "ASML.AMS",
                      "2. name": "ASML Holding N.V.",
                      "3. type": "Equity",
                      "4. region": "Amsterdam",
                      "8. currency": "EUR",
                      "9. matchScore": "0.7273"
                    }
                  ]
                }
                """;
        quoteResponse = """
                {
                  "Global Quote": {
                    "01. symbol": "ASML.AMS",
                    "05. price": "1489.8000",
                    "07. latest trading day": "2026-08-24"
                  }
                }
                """;

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/query", this::respond);
        server.start();
        client = new ProductionAlphaVantageClient(
                new ObjectMapper(),
                "http://localhost:" + server.getAddress().getPort() + "/query",
                "test-key",
                true);
    }

    @AfterEach
    void after()
    {
        if (server != null) server.stop(0);
    }

    @Test
    void readsMatchingQuarterAndNormalizesCashFlow() throws RequestFailureException
    {
        Optional<AlphaVantageIncomeStatement> income = client.getIncomeStatement(
                "NVDA", "25Q2", "2025-07");
        Optional<AlphaVantageCashFlow> cashFlow = client.getCashFlow(
                "NVDA", "25Q2", "2025-07");

        assertThat(income.isPresent(), is(true));
        assertThat(income.orElseThrow().revenue(), comparesEqualTo(new BigDecimal("100000000")));
        assertThat(income.orElseThrow().grossProfit(), comparesEqualTo(new BigDecimal("60000000")));
        assertThat(income.orElseThrow().operatingIncome(), comparesEqualTo(new BigDecimal("30000000")));
        assertThat(income.orElseThrow().netIncome(), comparesEqualTo(new BigDecimal("20000000")));
        assertThat(income.orElseThrow().reportedCurrency(), is("USD"));
        assertThat(cashFlow.isPresent(), is(true));
        assertThat(cashFlow.orElseThrow().dividend(), comparesEqualTo(new BigDecimal("5000000")));
        assertThat(cashFlow.orElseThrow().capex(), comparesEqualTo(new BigDecimal("10000000")));
        assertThat(cashFlow.orElseThrow().freeCashFlow(), comparesEqualTo(new BigDecimal("30000000")));
        assertThat(cashFlow.orElseThrow().reportedCurrency(), is("USD"));
    }

    @Test
    void returnsEmptyForUnsupportedHalfYearPeriods() throws RequestFailureException
    {
        assertThat(client.getIncomeStatement("NVDA", "25H1", "2025-06"), is(Optional.empty()));
        assertThat(client.getCashFlow("NVDA", "25H1", "2025-06"), is(Optional.empty()));
    }

    @Test
    void searchesTickersAndReadsLatestQuote() throws RequestFailureException
    {
        List<AlphaVantageTicker> tickers = client.searchTickers("ASML");
        Optional<AlphaVantageQuote> quote = client.getQuote("ASML.AMS");

        assertThat(tickers.size(), is(1));
        assertThat(tickers.get(0).symbol(), is("ASML.AMS"));
        assertThat(tickers.get(0).name(), is("ASML Holding N.V."));
        assertThat(tickers.get(0).type(), is("Equity"));
        assertThat(tickers.get(0).region(), is("Amsterdam"));
        assertThat(tickers.get(0).currency(), is("EUR"));
        assertThat(tickers.get(0).matchScore(), comparesEqualTo(new BigDecimal("0.7273")));
        assertThat(quote.isPresent(), is(true));
        assertThat(quote.orElseThrow().price(), comparesEqualTo(new BigDecimal("1489.8000")));
        assertThat(quote.orElseThrow().date(), is(LocalDate.of(2026, 8, 24)));
    }

    @Test
    void treatsApiInformationPayloadAsFailure()
    {
        incomeResponse = "{\"Information\":\"daily limit reached\"}";

        RequestFailureException exception = assertThrows(
                RequestFailureException.class,
                () -> client.getIncomeStatement("NVDA", "25Q2", "2025-07"));

        assertThat(exception.getMessage(), containsString("daily limit reached"));
    }

    private void respond(HttpExchange exchange) throws IOException
    {
        String query = exchange.getRequestURI().getRawQuery();
        String body;
        if (query.contains("function=CASH_FLOW")) {
            body = cashFlowResponse;
        } else if (query.contains("function=SYMBOL_SEARCH")) {
            body = tickerSearchResponse;
        } else if (query.contains("function=GLOBAL_QUOTE")) {
            body = quoteResponse;
        } else {
            body = incomeResponse;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
