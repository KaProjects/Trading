package org.kaleta.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageEarnings;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;
import org.kaleta.client.dto.AlphaVantagePriceRange;
import org.kaleta.client.dto.AlphaVantageQuote;
import org.kaleta.client.dto.AlphaVantageShares;
import org.kaleta.client.dto.AlphaVantageTicker;

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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@IfBuildProperty(name = "alphavantage.mode", stringValue = "real", enableIfMissing = true)
public class ProductionAlphaVantageClient implements AlphaVantageClient
{
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
            @ConfigProperty(name = "alphavantage.api.url") String apiUrl,
            @ConfigProperty(name = "alphavantage.apikey") String apiKey)
    {
        this(objectMapper, apiKey, apiUrl, REQUEST_INTERVAL_MILLIS);
    }

    ProductionAlphaVantageClient(ObjectMapper objectMapper, String apiUrl, String apiKey, boolean testMode)
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
                decimal(value, "netIncome"),
                value.path("reportedCurrency").asText(null)));
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
                freeCashFlow,
                value.path("reportedCurrency").asText(null)));
    }

    @Override
    public List<AlphaVantageTicker> searchTickers(String ticker) throws RequestFailureException
    {
        JsonNode matches = get("SYMBOL_SEARCH", "keywords", ticker).path("bestMatches");
        if (!matches.isArray()) return List.of();

        List<AlphaVantageTicker> result = new ArrayList<>();
        for (JsonNode match : matches) {
            String symbol = text(match, "1. symbol");
            if (symbol == null) continue;
            result.add(new AlphaVantageTicker(
                    symbol,
                    text(match, "2. name"),
                    text(match, "3. type"),
                    text(match, "4. region"),
                    text(match, "8. currency"),
                    optionalDecimal(match, "9. matchScore")));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<AlphaVantageQuote> getQuote(String ticker) throws RequestFailureException
    {
        JsonNode quote = get("GLOBAL_QUOTE", ticker).path("Global Quote");
        if (!quote.isObject() || quote.isEmpty()) return Optional.empty();

        BigDecimal price = decimal(quote, "05. price");
        String dateValue = text(quote, "07. latest trading day");
        if (price == null || dateValue == null || price.signum() <= 0) return Optional.empty();

        try {
            return Optional.of(new AlphaVantageQuote(price, LocalDate.parse(dateValue)));
        } catch (DateTimeParseException exception) {
            throw new RequestFailureException(
                    "Alpha Vantage returned invalid latest trading day '" + dateValue + "'",
                    exception);
        }
    }

    @Override
    public Optional<AlphaVantagePriceRange> getPriceRange(
            String ticker,
            String from,
            String to) throws RequestFailureException
    {
        LocalDate fromDate = parseDate(from, "price range start");
        LocalDate toDate = parseDate(to, "price range end");
        if (fromDate.isAfter(toDate)) {
            throw new RequestFailureException(
                    "Alpha Vantage price range start must not be after its end");
        }

        Map<String, String> additionalParameters = fromDate.isBefore(LocalDate.now().minusDays(120))
                ? Map.of("outputsize", "full")
                : Map.of();
        JsonNode timeSeries = get(
                "TIME_SERIES_DAILY",
                "symbol",
                ticker,
                additionalParameters).path("Time Series (Daily)");
        if (!timeSeries.isObject() || timeSeries.isEmpty()) return Optional.empty();

        BigDecimal high = null;
        BigDecimal low = null;
        Iterator<Map.Entry<String, JsonNode>> days = timeSeries.properties().iterator();
        while (days.hasNext()) {
            Map.Entry<String, JsonNode> day = days.next();
            LocalDate date;
            try {
                date = LocalDate.parse(day.getKey());
            } catch (DateTimeParseException ignored) {
                continue;
            }
            if (date.isBefore(fromDate) || date.isAfter(toDate)) continue;

            BigDecimal dailyHigh = decimal(day.getValue(), "2. high");
            BigDecimal dailyLow = decimal(day.getValue(), "3. low");
            if (dailyHigh != null && (high == null || dailyHigh.compareTo(high) > 0)) high = dailyHigh;
            if (dailyLow != null && (low == null || dailyLow.compareTo(low) < 0)) low = dailyLow;
        }
        return high == null && low == null
                ? Optional.empty()
                : Optional.of(new AlphaVantagePriceRange(high, low));
    }

    @Override
    public Optional<AlphaVantageShares> getShares(
            String ticker,
            String periodName,
            String endingMonth) throws RequestFailureException
    {
        Optional<JsonNode> report = getReport("BALANCE_SHEET", ticker, periodName, endingMonth);
        if (report.isEmpty()) return Optional.empty();

        JsonNode value = report.get();
        BigDecimal shares = decimal(value, "commonStockSharesOutstanding");
        return shares == null
                ? Optional.empty()
                : Optional.of(new AlphaVantageShares(
                        shares,
                        value.path("reportedCurrency").asText(null)));
    }

    @Override
    public Optional<AlphaVantageEarnings> getEarnings(
            String ticker,
            String periodName,
            String endingMonth) throws RequestFailureException
    {
        Optional<JsonNode> report = getEarningsReport(ticker, periodName, endingMonth);
        if (report.isEmpty()) return Optional.empty();

        BigDecimal reportedEps = decimal(report.get(), "reportedEPS");
        return reportedEps == null
                ? Optional.empty()
                : Optional.of(new AlphaVantageEarnings(reportedEps));
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

    private Optional<JsonNode> getEarningsReport(
            String ticker,
            String periodName,
            String endingMonth) throws RequestFailureException
    {
        String reportsField;
        if (periodName != null && periodName.matches("\\d{2}FY")) {
            reportsField = "annualEarnings";
        } else if (periodName != null && periodName.matches("\\d{2}Q[1-4]")) {
            reportsField = "quarterlyEarnings";
        } else {
            return Optional.empty();
        }

        YearMonth expectedEndingMonth;
        try {
            expectedEndingMonth = YearMonth.parse(endingMonth);
        } catch (DateTimeParseException exception) {
            throw new RequestFailureException(
                    "Invalid ending month '" + endingMonth + "' for Alpha Vantage request",
                    exception);
        }

        JsonNode reports = get("EARNINGS", ticker).path(reportsField);
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
        return get(function, "symbol", ticker);
    }

    private JsonNode get(String function, String argumentName, String argumentValue)
            throws RequestFailureException
    {
        return get(function, argumentName, argumentValue, Map.of());
    }

    private JsonNode get(
            String function,
            String argumentName,
            String argumentValue,
            Map<String, String> additionalParameters) throws RequestFailureException
    {
        StringBuilder uriValue = new StringBuilder(apiUrl)
                .append("?function=").append(encode(function))
                .append("&").append(encode(argumentName)).append("=").append(encode(argumentValue));
        additionalParameters.forEach((name, value) -> uriValue
                .append("&").append(encode(name)).append("=").append(encode(value)));
        uriValue.append("&apikey=").append(encode(apiKey));
        URI uri = URI.create(uriValue.toString());
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

    private static BigDecimal optionalDecimal(JsonNode value, String field)
    {
        String text = text(value, field);
        if (text == null) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static LocalDate parseDate(String value, String field) throws RequestFailureException
    {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new RequestFailureException(
                    "Invalid " + field + " '" + value + "' for Alpha Vantage request",
                    exception);
        }
    }

    private static String text(JsonNode value, String field)
    {
        String text = value.path(field).asText(null);
        return text == null || text.isBlank() ? null : text;
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
