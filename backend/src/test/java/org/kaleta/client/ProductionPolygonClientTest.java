package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonPriceRange;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionPolygonClientTest
{
    private HttpServer server;
    private String financialsResponse;
    private ProductionPolygonClient client;

    @BeforeEach
    void before() throws IOException
    {
        financialsResponse = financialsResponse("USD", "USD", "USD", "USD");
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        client = new ProductionPolygonClient(
                new ObjectMapper(),
                "http://localhost:" + server.getAddress().getPort(),
                "test-key");
    }

    @AfterEach
    void after()
    {
        if (server != null) server.stop(0);
    }

    @Test
    void readsFinancialCurrencyAndTreatsPricesAsUsd() throws RequestFailureException
    {
        Optional<PolygonFinancials> financials = client.getFinancials("AMD", "2026", "Q2");
        Optional<PolygonPriceRange> priceRange = client.getPriceRange(
                "AMD",
                "2026-04-01",
                "2026-06-30");

        assertThat(financials.isPresent(), is(true));
        assertThat(financials.orElseThrow().shares(), comparesEqualTo(new BigDecimal("1000000")));
        assertThat(financials.orElseThrow().revenue(), comparesEqualTo(new BigDecimal("2000000")));
        assertThat(financials.orElseThrow().reportedCurrency(), is("USD"));
        assertThat(priceRange.isPresent(), is(true));
        assertThat(priceRange.orElseThrow().high(), comparesEqualTo(new BigDecimal("13.50")));
        assertThat(priceRange.orElseThrow().low(), comparesEqualTo(new BigDecimal("10.00")));
        assertThat(priceRange.orElseThrow().reportedCurrency(), is("USD"));
    }

    @Test
    void rejectsFinancialsWithInconsistentCurrencies()
    {
        financialsResponse = financialsResponse("USD", "EUR", "USD", "USD");

        RequestFailureException exception = assertThrows(
                RequestFailureException.class,
                () -> client.getFinancials("AMD", "2026", "Q2"));

        assertThat(
                exception.getMessage(),
                containsString("Polygon.io financial metrics use inconsistent currencies: EUR, USD"));
    }

    private void respond(HttpExchange exchange) throws IOException
    {
        String path = exchange.getRequestURI().getPath();
        String body;
        if (path.contains("/reference/financials")) {
            body = financialsResponse;
        } else if (path.contains("/aggs/ticker/")) {
            body = "{\"results\":[{\"h\":12.50,\"l\":10.00},{\"h\":13.50,\"l\":11.00}]}";
        } else {
            body = "{}";
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String financialsResponse(
            String revenueCurrency,
            String grossProfitCurrency,
            String operatingIncomeCurrency,
            String netIncomeCurrency)
    {
        return """
                {
                  "results": [
                    {
                      "fiscal_period": "Q2",
                      "fiscal_year": "2026",
                      "financials": {
                        "income_statement": {
                          "basic_average_shares": {"value": 1000000, "unit": "shares"},
                          "revenues": {"value": 2000000, "unit": "%s"},
                          "gross_profit": {"value": 1200000, "unit": "%s"},
                          "operating_income_loss": {"value": 800000, "unit": "%s"},
                          "net_income_loss": {"value": 600000, "unit": "%s"}
                        }
                      }
                    }
                  ]
                }
                """.formatted(
                revenueCurrency,
                grossProfitCurrency,
                operatingIncomeCurrency,
                netIncomeCurrency);
    }
}
