package org.kaleta.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@IfBuildProperty(name = "alphavantage.mode", stringValue = "real", enableIfMissing = true)
public class ProductionAlphaVantageClient implements AlphaVantageClient
{
    private static final String API_URL = "https://www.alphavantage.co/query";
    private static final long REQUEST_INTERVAL_MILLIS = 1_100;

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final long requestIntervalNanos;
    private final Object requestLock = new Object();
    private long lastRequestCompletedNanos;
    private boolean requestMade;

    @Inject
    public ProductionAlphaVantageClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "alphavantage.apikey") String apiKey)
    {
        this(objectMapper, apiKey, API_URL, REQUEST_INTERVAL_MILLIS);
    }

    ProductionAlphaVantageClient(ObjectMapper objectMapper, String apiKey, String apiUrl)
    {
        this(objectMapper, apiKey, apiUrl, 0);
    }

    private ProductionAlphaVantageClient(
            ObjectMapper objectMapper,
            String apiKey,
            String apiUrl,
            long requestIntervalMillis)
    {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.requestIntervalNanos = TimeUnit.MILLISECONDS.toNanos(requestIntervalMillis);
    }

    @Override
    public Optional<AlphaVantageIncomeStatement> getIncomeStatement(
            String ticker,
            String periodName,
            String endingMonth) throws RequestFailureException
    {
        Optional<JsonNode> report = getReport("INCOME_STATEMENT", ticker, periodName, endingMonth);
        if (report.isEmpty()) return Optional.empty();

        JsonNode value = report.get();
        return Optional.of(new AlphaVantageIncomeStatement(
                decimal(value, "totalRevenue"),
                decimal(value, "grossProfit"),
                decimal(value, "operatingIncome"),
                decimal(value, "netIncome")));
    }

    @Override
    public Optional<AlphaVantageCashFlow> getCashFlow(
            String ticker,
            String periodName,
            String endingMonth) throws RequestFailureException
    {
        Optional<JsonNode> report = getReport("CASH_FLOW", ticker, periodName, endingMonth);
        if (report.isEmpty()) return Optional.empty();

        JsonNode value = report.get();
        BigDecimal operatingCashFlow = decimal(value, "operatingCashflow");
        BigDecimal capex = normalizeCapex(decimal(value, "capitalExpenditures"));
        BigDecimal dividend = decimal(value, "dividendPayout");
        BigDecimal freeCashFlow = operatingCashFlow == null || capex == null
                ? null
                : operatingCashFlow.subtract(capex);

        return Optional.of(new AlphaVantageCashFlow(
                dividend == null ? null : dividend.abs(),
                capex,
                freeCashFlow));
    }

    private Optional<JsonNode> getReport(
            String function,
            String ticker,
            String periodName,
            String endingMonth) throws RequestFailureException
    {
        String reportsField = reportsField(periodName);
        if (reportsField == null) return Optional.empty();

        YearMonth expectedEndingMonth;
        try {
            expectedEndingMonth = YearMonth.parse(endingMonth);
        } catch (DateTimeParseException exception) {
            throw new RequestFailureException(
                    "Invalid ending month '" + endingMonth + "' for Alpha Vantage request",
                    exception);
        }

        JsonNode reports = get(function, ticker).path(reportsField);
        if (!reports.isArray()) return Optional.empty();

        for (JsonNode report : reports) {
            String fiscalDate = report.path("fiscalDateEnding").asText(null);
            if (fiscalDate == null) continue;
            try {
                if (YearMonth.from(LocalDate.parse(fiscalDate)).equals(expectedEndingMonth)) {
                    return Optional.of(report);
                }
            } catch (DateTimeParseException ignored) {
            }
        }
        return Optional.empty();
    }

    private JsonNode get(String function, String ticker) throws RequestFailureException
    {
        URI uri = URI.create(apiUrl
                + "?function=" + encode(function)
                + "&symbol=" + encode(ticker)
                + "&apikey=" + encode(apiKey));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        synchronized (requestLock) {
            awaitRequestSlot();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode body = objectMapper.readTree(response.body());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RequestFailureException(errorMessage(response.statusCode(), body));
                }

                String apiError = apiError(body);
                if (apiError != null) {
                    throw new RequestFailureException("Alpha Vantage request failed: " + apiError);
                }
                return body;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RequestFailureException("Alpha Vantage request was interrupted", exception);
            } catch (IOException exception) {
                throw new RequestFailureException(
                        "Alpha Vantage request failed: " + exception.getMessage(),
                        exception);
            } finally {
                lastRequestCompletedNanos = System.nanoTime();
                requestMade = true;
            }
        }
    }

    private void awaitRequestSlot() throws RequestFailureException
    {
        if (requestMade) {
            long remainingNanos = requestIntervalNanos - (System.nanoTime() - lastRequestCompletedNanos);
            if (remainingNanos > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(remainingNanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RequestFailureException(
                            "Alpha Vantage request was interrupted while waiting for the rate limit",
                            exception);
                }
            }
        }
    }

    private String errorMessage(int statusCode, JsonNode body)
    {
        String reason = apiError(body);
        String message = "Alpha Vantage returned HTTP " + statusCode;
        return reason == null || reason.isBlank() ? message : message + ": " + reason;
    }

    private String apiError(JsonNode body)
    {
        if (body == null) return null;

        for (String field : new String[]{"Error Message", "Information", "Note"}) {
            String message = body.path(field).asText(null);
            if (message != null && !message.isBlank()) return message;
        }
        return null;
    }

    private static String reportsField(String periodName)
    {
        if (periodName != null && periodName.matches("\\d{2}FY")) return "annualReports";
        if (periodName != null && periodName.matches("\\d{2}Q[1-4]")) return "quarterlyReports";
        return null;
    }

    private static BigDecimal decimal(JsonNode report, String field) throws RequestFailureException
    {
        String value = report.path(field).asText(null);
        if (value == null || value.isBlank() || "None".equalsIgnoreCase(value)) return null;

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new RequestFailureException(
                    "Alpha Vantage returned invalid value '" + value + "' for " + field,
                    exception);
        }
    }

    private static BigDecimal normalizeCapex(BigDecimal capex)
    {
        return capex == null ? null : capex.abs();
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
