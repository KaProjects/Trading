package org.kaleta.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.FinnhubQuote;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
@IfBuildProperty(name = "finnhub.mode", stringValue = "real", enableIfMissing = true)
public class ProductionFinnhubClient implements FinnhubClient
{
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String authQuery;

    public ProductionFinnhubClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "finnhub.api.url") String apiUrl,
            @ConfigProperty(name = "finnhub.apikey") String apiKey)
    {
        this.client = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
        this.authQuery = "&token=" + apiKey;
    }

    @Override
    public FinnhubQuote quote(String ticker) throws RequestFailureException
    {
        HttpRequest request = HttpRequest.newBuilder().GET()
                .uri(URI.create(apiUrl + "quote?symbol=" + ticker + authQuery)).build();
        try
        {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), FinnhubQuote.class);
            } else {
                throw new RequestFailureException(errorMessage(response));
            }
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new RequestFailureException("Finnhub request was interrupted", exception);
        }
        catch (IOException exception)
        {
            throw new RequestFailureException("Finnhub request failed: " + exception.getMessage(), exception);
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

        String message = "Finnhub returned HTTP " + response.statusCode();
        return reason == null || reason.isBlank() ? message : message + ": " + reason;
    }
}
