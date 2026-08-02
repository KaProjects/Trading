package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.FinnhubQuote;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

@Startup
@ApplicationScoped
@IfBuildProperty(name = "finnhub.mode", stringValue = "fake")
public class InMemoryFinnhubClient implements FinnhubClient
{
    private final Map<String, FinnhubQuote> quotes;

    @Inject
    public InMemoryFinnhubClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "finnhub.data.file") String dataFile)
    {
        FinnhubData data = load(objectMapper, dataFile);
        this.quotes = data.quotes() == null ? Map.of() : data.quotes();
    }

    @Override
    public FinnhubQuote quote(String ticker)
    {
        return quotes.get(ticker.toUpperCase(Locale.ROOT));
    }

    private FinnhubData load(ObjectMapper objectMapper, String dataFile)
    {
        Path path = Path.of(dataFile).toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(path)) {
            return objectMapper.readValue(input, FinnhubData.class);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to initialize fake Finnhub client from '" + path + "'",
                    exception);
        }
    }

    private record FinnhubData(Map<String, FinnhubQuote> quotes)
    {
    }
}
