package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.GeminiFinancials;
import org.kaleta.client.dto.GeminiTargets;

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
@IfBuildProperty(name = "gemini.mode", stringValue = "fake")
public class InMemoryGeminiClient implements GeminiClient
{
    private final Map<String, GeminiTargets> companyTargets;
    private final Map<String, GeminiFinancials> companyFinancials;
    private final long delayMillis;

    @Inject
    public InMemoryGeminiClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "gemini.data.file") String dataFile,
            @ConfigProperty(name = "gemini.fake.delay.millis", defaultValue = "1500") long delayMillis)
    {
        GeminiData data = load(objectMapper, dataFile);
        this.companyTargets = data.companyTargets() == null ? Map.of() : data.companyTargets();
        this.companyFinancials = data.companyFinancials() == null ? Map.of() : data.companyFinancials();
        this.delayMillis = delayMillis;
    }

    @Override
    public GeminiTargets getCompanyTargets(
            String ticker,
            List<String> institutions,
            LocalDate from,
            LocalDate to)
    {
        sleep();

        GeminiTargets targets = companyTargets.get(ticker.toUpperCase(Locale.ROOT));
        if (targets == null) {
            return new GeminiTargets(
                    List.of(),
                    new GeminiTargets.Report(
                            "No trusted institution published a price target for " + ticker
                                    + " between " + from + " and " + to + ".",
                            List.of()));
        }

        List<GeminiTargets.Target> inWindow = targets.targets().stream()
                .filter(target -> withinWindow(target.date(), from, to))
                .filter(target -> institutions.stream().anyMatch(institution ->
                        institution.equalsIgnoreCase(target.institution())))
                .toList();

        return new GeminiTargets(inWindow, targets.report());
    }

    @Override
    public GeminiFinancials getCompanyFinancials(String ticker, int quarters)
    {
        sleep();

        GeminiFinancials financials = companyFinancials.get(ticker.toUpperCase(Locale.ROOT));
        if (financials == null) {
            return new GeminiFinancials(List.of(), List.of("No quarterly data available for " + ticker + "."));
        }

        return new GeminiFinancials(
                financials.quarters().stream().limit(quarters).toList(),
                financials.notes() == null ? List.of() : financials.notes());
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

    private void sleep()
    {
        if (delayMillis <= 0) return;
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private GeminiData load(ObjectMapper objectMapper, String dataFile)
    {
        Path path = Path.of(dataFile).toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(path)) {
            return objectMapper.readValue(input, GeminiData.class);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to initialize fake Gemini client from '" + path + "'",
                    exception);
        }
    }

    private record GeminiData(
            Map<String, GeminiTargets> companyTargets,
            Map<String, GeminiFinancials> companyFinancials)
    {
    }
}
