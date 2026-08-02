package org.kaleta.client;

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
    private static final String API_PATH = "https://finnhub.io/api/v1/";

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String authQuery;

    public ProductionFinnhubClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "finnhub.apikey") String apiKey)
    {
        this.client = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.authQuery = "&token=" + apiKey;
    }

    @Override
    public FinnhubQuote quote(String ticker) throws RequestFailureException
    {
        HttpRequest request = HttpRequest.newBuilder().GET()
                .uri(URI.create(API_PATH + "quote?symbol=" + ticker + authQuery)).build();
        try
        {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), FinnhubQuote.class);
            } else {
                throw new RequestFailureException("request failed: " +  response.statusCode());
            }
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new RequestFailureException(exception);
        }
        catch (IOException exception)
        {
            throw new RequestFailureException(exception);
        }
    }
}
