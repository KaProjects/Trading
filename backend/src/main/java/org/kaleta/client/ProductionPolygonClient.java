package org.kaleta.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonPriceRange;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@IfBuildProperty(name = "polygon.mode", stringValue = "real", enableIfMissing = true)
public class ProductionPolygonClient implements PolygonClient
{
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;

    public ProductionPolygonClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "polygon.api.url") String apiUrl,
            @ConfigProperty(name = "polygon.apikey") String apiKey)
    {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
    }

    @Override
    public Optional<PolygonFinancials> getFinancials(
            String ticker,
            String fiscalYear,
            String fiscalPeriod) throws RequestFailureException
    {
        String timeframe = null;
        if (fiscalPeriod.startsWith("Q")) timeframe = "quarterly";
        if (fiscalPeriod.startsWith("F")) timeframe = "annual";

        StringBuilder endpoint = new StringBuilder(apiUrl)
                .append("/vX/reference/financials?ticker=")
                .append(encode(ticker));
        if (timeframe != null) endpoint.append("&timeframe=").append(timeframe);

        FinancialsResponse response = get(URI.create(endpoint.toString()), FinancialsResponse.class);
        return optionalList(response.results()).stream()
                .filter(result -> fiscalYear.equals(result.fiscalYear())
                        && fiscalPeriod.equals(result.fiscalPeriod()))
                .findFirst()
                .map(ProductionPolygonClient::toFinancials);
    }

    @Override
    public Optional<PolygonPriceRange> getPriceRange(
            String ticker,
            String from,
            String to) throws RequestFailureException
    {
        URI endpoint = URI.create(apiUrl
                + "/v2/aggs/ticker/" + encode(ticker)
                + "/range/1/day/" + encode(from)
                + "/" + encode(to));
        AggregatesResponse response = get(endpoint, AggregatesResponse.class);
        List<Aggregate> aggregates = optionalList(response.results());
        if (aggregates.isEmpty()) return Optional.empty();

        BigDecimal high = aggregates.stream()
                .map(Aggregate::high)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        BigDecimal low = aggregates.stream()
                .map(Aggregate::low)
                .filter(value -> value != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (high == null && low == null) return Optional.empty();
        return Optional.of(new PolygonPriceRange(high, low));
    }

    private <T> T get(URI uri, Class<T> responseType) throws RequestFailureException
    {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RequestFailureException(errorMessage(response));
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RequestFailureException("Polygon.io request was interrupted", exception);
        } catch (IOException exception) {
            throw new RequestFailureException("Polygon.io request failed: " + exception.getMessage(), exception);
        }
    }

    private String errorMessage(HttpResponse<String> response)
    {
        String reason = null;
        try {
            JsonNode body = objectMapper.readTree(response.body());
            reason = body.path("error").asText(null);
            if (reason == null) reason = body.path("message").asText(null);
        } catch (Exception ignored) {
        }

        String message = "Polygon.io returned HTTP " + response.statusCode();
        return reason == null || reason.isBlank() ? message : message + ": " + reason;
    }

    private static PolygonFinancials toFinancials(FinancialResult result)
    {
        IncomeStatement statement = result.financials() == null
                ? null
                : result.financials().incomeStatement();
        return new PolygonFinancials(
                value(statement == null ? null : statement.basicAverageShares()),
                value(statement == null ? null : statement.revenues()),
                value(statement == null ? null : statement.grossProfit()),
                value(statement == null ? null : statement.operatingIncomeLoss()),
                value(statement == null ? null : statement.netIncomeLoss()));
    }

    private static BigDecimal value(Metric metric)
    {
        return metric == null ? null : metric.value();
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static <T> List<T> optionalList(List<T> values)
    {
        return values == null ? List.of() : values;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    private record FinancialsResponse(List<FinancialResult> results)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    private record FinancialResult(
            @JsonProperty("fiscal_period") String fiscalPeriod,
            @JsonProperty("fiscal_year") String fiscalYear,
            FinancialStatements financials)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    private record FinancialStatements(
            @JsonProperty("income_statement") IncomeStatement incomeStatement)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    private record IncomeStatement(
            @JsonProperty("basic_average_shares") Metric basicAverageShares,
            Metric revenues,
            @JsonProperty("gross_profit") Metric grossProfit,
            @JsonProperty("operating_income_loss") Metric operatingIncomeLoss,
            @JsonProperty("net_income_loss") Metric netIncomeLoss)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    private record Metric(BigDecimal value)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    private record AggregatesResponse(List<Aggregate> results)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    private record Aggregate(
            @JsonProperty("h") BigDecimal high,
            @JsonProperty("l") BigDecimal low)
    {
    }
}
