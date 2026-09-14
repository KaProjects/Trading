package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.FinnhubEarnings;
import org.kaleta.client.dto.FinnhubQuote;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Startup
@ApplicationScoped
@IfBuildProperty(name = "finnhub.mode", stringValue = "fake")
public class InMemoryFinnhubClient implements FinnhubClient
{
    private final Map<String, FinnhubQuote> quotes;
    private final Map<String, List<FinnhubEarnings>> earningsCalendar;

    @Inject
    public InMemoryFinnhubClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "finnhub.data.file") String dataFile)
    {
        FinnhubData data = load(objectMapper, dataFile);
        this.quotes = data.quotes() == null ? Map.of() : data.quotes();
        this.earningsCalendar = data.earningsCalendar() == null ? Map.of() : data.earningsCalendar();
    }

    @Override
    public FinnhubQuote quote(String ticker)
    {
        return quotes.get(ticker.toUpperCase(Locale.ROOT));
    }

    @Override
    public List<FinnhubEarnings> earningsCalendar(String ticker, LocalDate from, LocalDate to)
    {
        return earningsCalendar.getOrDefault(ticker.toUpperCase(Locale.ROOT), List.of()).stream()
                .filter(earnings -> withinWindow(earnings.date(), from, to))
                .toList();
    }

    private static boolean withinWindow(String date, LocalDate from, LocalDate to)
    {
        try {
            LocalDate value = LocalDate.parse(date);
            return !value.isBefore(from) && !value.isAfter(to);
        } catch (RuntimeException exception) {
            return false;
        }
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

    private record FinnhubData(
            Map<String, FinnhubQuote> quotes,
            Map<String, List<FinnhubEarnings>> earningsCalendar)
    {
    }
}
