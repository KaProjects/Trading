package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import org.kaleta.client.dto.FinnhubQuote;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
public class FinnhubClient
{
    private final HttpClient client = HttpClient.newHttpClient();
    private final String api_path = "https://finnhub.io/api/v1/";
    private final String auth_query = "&token=" + ConfigProvider.getConfig().getValue("finnhub.apikey", String.class);

    public FinnhubQuote quote(String ticker) throws RequestFailureException
    {
        HttpRequest request = HttpRequest.newBuilder().GET()
                .uri(URI.create(api_path + "quote?symbol=" + ticker + auth_query)).build();
        try
        {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(response.body(), FinnhubQuote.class);
            } else {
                throw new RequestFailureException("request failed: " +  response.statusCode());
            }
        }
        catch (IOException | InterruptedException e)
        {
            throw new RequestFailureException(e);
        }
    }
}
