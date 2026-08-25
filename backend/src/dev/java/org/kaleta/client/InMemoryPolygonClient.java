package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonCompanyProfile;
import org.kaleta.client.dto.PolygonPriceRange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Startup
@ApplicationScoped
@IfBuildProperty(name = "polygon.mode", stringValue = "fake")
public class InMemoryPolygonClient implements PolygonClient
{
    private final Map<String, Map<String, PolygonFinancials>> financials;
    private final Map<String, Map<String, PolygonPriceRange>> priceRanges;
    private final Map<String, PolygonCompanyProfile> companyProfiles;

    @Inject
    public InMemoryPolygonClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "polygon.data.file") String dataFile)
    {
        PolygonData data = load(objectMapper, dataFile);
        this.financials = data.financials() == null ? Map.of() : data.financials();
        this.priceRanges = data.priceRanges() == null ? Map.of() : data.priceRanges();
        this.companyProfiles = data.companyProfiles() == null ? Map.of() : data.companyProfiles();
    }

    @Override
    public Optional<PolygonCompanyProfile> getCompanyProfile(String ticker)
    {
        return Optional.ofNullable(companyProfiles.get(normalize(ticker)));
    }

    @Override
    public Optional<PolygonFinancials> getFinancials(
            String ticker,
            String fiscalYear,
            String fiscalPeriod)
    {
        return Optional.ofNullable(financials
                .getOrDefault(normalize(ticker), Map.of())
                .get(fiscalYear + fiscalPeriod));
    }

    @Override
    public Optional<PolygonPriceRange> getPriceRange(String ticker, String from, String to)
    {
        return Optional.ofNullable(priceRanges
                .getOrDefault(normalize(ticker), Map.of())
                .get(from + ":" + to));
    }

    private PolygonData load(ObjectMapper objectMapper, String dataFile)
    {
        Path path = Path.of(dataFile).toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(path)) {
            return objectMapper.readValue(input, PolygonData.class);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to initialize fake Polygon client from '" + path + "'",
                    exception);
        }
    }

    private String normalize(String ticker)
    {
        return ticker.toUpperCase(Locale.ROOT);
    }

    private record PolygonData(
            Map<String, Map<String, PolygonFinancials>> financials,
            Map<String, Map<String, PolygonPriceRange>> priceRanges,
            Map<String, PolygonCompanyProfile> companyProfiles)
    {
    }
}
