package org.kaleta.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.GeminiFinancials;
import org.kaleta.client.dto.GeminiTargets;

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
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@IfBuildProperty(name = "gemini.mode", stringValue = "real", enableIfMissing = true)
public class ProductionGeminiClient implements GeminiClient
{
    private static final int TARGET_OVERVIEW_MAX_LENGTH = 1000;
    private static final int TARGET_TAKEAWAY_MAX_LENGTH = 500;
    private static final int TARGET_TAKEAWAYS_MAX_COUNT = 4;
    private static final int REPORT_TAKEAWAYS_MAX_COUNT = 6;

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public ProductionGeminiClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "gemini.api.url") String apiUrl,
            @ConfigProperty(name = "gemini.apikey", defaultValue = "") String apiKey,
            @ConfigProperty(name = "gemini.model") String model,
            @ConfigProperty(name = "gemini.timeout.seconds", defaultValue = "300") long timeoutSeconds)
    {
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    @Override
    public GeminiTargets getCompanyTargets(
            String ticker,
            List<String> institutions,
            LocalDate from,
            LocalDate to) throws RequestFailureException
    {
        String prompt = targetsPrompt(ticker, institutions, from, to);
        JsonNode response = generate(prompt, targetsSchema());
        return objectMapper.convertValue(response, GeminiTargets.class);
    }

    @Override
    public GeminiFinancials getCompanyFinancials(String ticker, int quarters) throws RequestFailureException
    {
        String prompt = financialsPrompt(ticker, quarters);
        JsonNode response = generate(prompt, financialsSchema());
        return objectMapper.convertValue(response, GeminiFinancials.class);
    }

    private JsonNode generate(String prompt, ObjectNode responseSchema) throws RequestFailureException
    {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode content = contents.addObject();
        content.putArray("parts").addObject().put("text", prompt);
        body.putArray("tools").addObject().putObject("google_search");

        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseJsonSchema", responseSchema);

        URI endpoint = URI.create(apiUrl + "/models/" + encode(model) + ":generateContent");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RequestFailureException(errorMessage(response));
            }
            return objectMapper.readTree(responseText(objectMapper.readTree(response.body())));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RequestFailureException("Gemini request was interrupted", exception);
        } catch (IOException exception) {
            throw new RequestFailureException("Gemini request failed: " + exception.getMessage(), exception);
        }
    }

    private String responseText(JsonNode response) throws RequestFailureException
    {
        StringBuilder text = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            if (part.hasNonNull("text")) text.append(part.get("text").asText());
        }
        if (text.isEmpty()) {
            throw new RequestFailureException("Gemini returned no content");
        }
        return text.toString();
    }

    private ObjectNode targetsSchema()
    {
        ObjectNode target = object();
        ObjectNode targetProperties = target.putObject("properties");
        targetProperties.set("institution", string("Canonical name of the institution that issued the price target."));
        targetProperties.set("date", string("Date the action was announced, in YYYY-MM-DD format."));
        targetProperties.set("price", number("Newly announced target price in USD."));
        targetProperties.set("rating", nullableString("Rating exactly as stated by the source, or null."));
        targetProperties.set("source", string("Direct public URL supporting the target, or the source hostname."));
        targetProperties.set("overview", string(
                "Concise overview of the research behind this target, at most "
                        + TARGET_OVERVIEW_MAX_LENGTH + " characters."));
        targetProperties.set("keyTakeaways", stringArray(
                "One to " + TARGET_TAKEAWAYS_MAX_COUNT + " self-contained takeaways, each at most "
                        + TARGET_TAKEAWAY_MAX_LENGTH + " characters."));
        target.set("required", strings("institution", "date", "price", "rating", "source", "overview", "keyTakeaways"));

        ObjectNode report = object();
        ObjectNode reportProperties = report.putObject("properties");
        reportProperties.set("overview", string(
                "Merged research overview combining every returned target into one view of what these "
                        + "institutions think about the company."));
        reportProperties.set("keyTakeaways", stringArray(
                "One to " + REPORT_TAKEAWAYS_MAX_COUNT + " merged takeaways across all returned targets."));
        report.set("required", strings("overview", "keyTakeaways"));

        ObjectNode schema = object();
        ObjectNode properties = schema.putObject("properties");
        ObjectNode targets = properties.putObject("targets");
        targets.put("type", "array");
        targets.set("items", target);
        properties.set("report", report);
        schema.set("required", strings("targets", "report"));
        return schema;
    }

    private ObjectNode financialsSchema()
    {
        ObjectNode quarter = object();
        ObjectNode properties = quarter.putObject("properties");
        properties.set("id", string("Fiscal quarter identifier in YYQX format, for example 26Q2."));
        properties.set("name", string("Human-readable fiscal quarter name, for example Q2 2026."));
        properties.set("endingMonth", string("Fiscal quarter ending month in YYYY-MM format."));
        properties.set("reportDate", string("Date the quarter was reported, in YYYY-MM-DD format."));
        properties.set("revenue", nullableNumber(
                "Reported revenue in millions of the company's original reporting currency; "
                        + "16130 means 16.13 billion."));
        properties.set("grossProfit", nullableNumber("Reported gross profit in millions, or null."));
        properties.set("operatingIncome", nullableNumber("Reported operating income in millions, or null."));
        properties.set("netIncome", nullableNumber("Reported net income in millions, or null."));
        properties.set("capex", nullableNumber(
                "Capital expenditures for the individual quarter in millions, as a positive cash outflow, or null."));
        properties.set("freeCashFlow", nullableNumber(
                "Free cash flow for the individual quarter in millions, or null."));
        properties.set("dividend", nullableNumber("Total dividends paid in the quarter in millions, or null."));
        properties.set("shares", nullableNumber(
                "Reported number of shares in millions of shares; 5104 means 5.104 billion shares."));
        properties.set("adjustedEps", nullableNumber(
                "Adjusted earnings per share for the quarter in the original reporting currency, or null."));
        properties.set("priceMin", nullableNumber(
                "Minimum share price between the previous and this report date, excluding both edge dates."));
        properties.set("priceMax", nullableNumber(
                "Maximum share price between the previous and this report date, excluding both edge dates."));
        quarter.set("required", strings("id", "name", "endingMonth", "reportDate", "revenue", "grossProfit",
                "operatingIncome", "netIncome", "capex", "freeCashFlow", "dividend", "shares", "adjustedEps",
                "priceMin", "priceMax"));

        ObjectNode schema = object();
        ObjectNode schemaProperties = schema.putObject("properties");
        ObjectNode quarters = schemaProperties.putObject("quarters");
        quarters.put("type", "array");
        quarters.set("items", quarter);
        schemaProperties.set("notes", stringArray(
                "Concise retrieval issues, uncertainties, conflicting sources or unavailable values; "
                        + "empty when none occurred."));
        schema.set("required", strings("quarters", "notes"));
        return schema;
    }

    private String financialsPrompt(String ticker, int quarters)
    {
        return """
            For the company with ticker %s, retrieve the reported financials of its last %d already
            reported fiscal quarters, newest first.

            Use Google Search extensively and prefer the company's own quarterly reports, press
            releases and filings over secondary sources.

            For every quarter set the fiscal quarter name, the quarter id in the required YYQX format,
            the ending month and the date on which that quarter was reported. Double-check the
            quarter ids, names and dates; a quarter counts as reported only when its results are
            already published.

            Report every monetary value in millions of the company's original reporting currency,
            never converted into another currency, and adjusted earnings per share per share in that
            same currency. Capital expenditures are a positive cash outflow amount. Every value is for
            the individual quarter, never year-to-date or trailing twelve months.

            Use null for any value you cannot verify, and list what was missing, uncertain or
            contradictory between sources in notes. Never invent a value.
            """.formatted(ticker, quarters);
    }

    private String targetsPrompt(String ticker, List<String> institutions, LocalDate from, LocalDate to)
    {
        return """
            You are a financial-data researcher extracting institutional equity analyst price-target
            actions for a single company.

            COMPANY TICKER:
            %s

            INCLUSIVE DATE WINDOW:
            %s through %s

            TRUSTED INSTITUTIONS:
            %s

            Use Google Search extensively. Search for price-target actions announced for this ticker
            during this exact date interval by the trusted institutions listed above. Return only
            targets issued by those institutions; ignore every other provider. An empty targets list
            is the correct result when none of them published a target in the window.

            A returned target must satisfy every rule below:

            - Its source explicitly identifies the company or ticker, institution, new target price
              and action date.
            - date is the date the action was announced, not the publication date of a later article
              repeating an older action.
            - price is the newly announced target, not the previous target, the current share price,
              a consensus target or an algorithmic forecast.
            - price is in USD. Exclude the record when its currency cannot be verified.
            - rating preserves the source's wording and is never inferred; use null when no rating is
              stated.
            - source supports all returned facts. Never invent or reconstruct a URL.
            - Prefer an institution publication, then a reputable financial publication or wire
              service, then an established analyst-action database.

            Reddit is never an acceptable source. Exclude consensus targets, anonymous analysts,
            rumors, blogs, social-media posts, unsupported search snippets, stale reports republished
            during the interval and duplicate syndicated reports. Deduplicate by institution, date
            and price, keeping the strongest source.

            For every returned target, research the institution's stated rationale, the material
            company or industry developments supporting it, significant catalysts, assumptions and
            risks. Write its overview and key takeaways from that research. Distinguish verified
            facts from analyst opinions and do not infer an unstated rationale.

            Then merge every returned target into a single research report describing what these
            institutions currently think about the company: where they agree, where they disagree,
            and what drives the range of targets. When the targets list is empty, say so in the
            report overview and return an empty list of report takeaways.

            Verify every field against its source before returning the result.
            """.formatted(ticker, from, to, String.join(", ", institutions));
    }

    private ObjectNode object()
    {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        return node;
    }

    private ObjectNode string(String description)
    {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "string");
        node.put("description", description);
        return node;
    }

    private ObjectNode nullableString(String description)
    {
        ObjectNode node = objectMapper.createObjectNode();
        node.putArray("type").add("string").add("null");
        node.put("description", description);
        return node;
    }

    private ObjectNode nullableNumber(String description)
    {
        ObjectNode node = objectMapper.createObjectNode();
        node.putArray("type").add("number").add("null");
        node.put("description", description);
        return node;
    }

    private ObjectNode number(String description)
    {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "number");
        node.put("description", description);
        return node;
    }

    private ObjectNode stringArray(String description)
    {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "array");
        node.put("description", description);
        node.putObject("items").put("type", "string");
        return node;
    }

    private ArrayNode strings(String... values)
    {
        ArrayNode node = objectMapper.createArrayNode();
        for (String value : values) node.add(value);
        return node;
    }

    private String errorMessage(HttpResponse<String> response)
    {
        String reason = null;
        try {
            JsonNode body = objectMapper.readTree(response.body());
            reason = body.path("error").path("message").asText(null);
        } catch (Exception ignored) {
        }

        String message = "Gemini returned HTTP " + response.statusCode();
        return reason == null || reason.isBlank() ? message : message + ": " + reason;
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
