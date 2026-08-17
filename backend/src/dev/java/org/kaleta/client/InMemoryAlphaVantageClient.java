package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Startup
@ApplicationScoped
@IfBuildProperty(name = "alphavantage.mode", stringValue = "fake")
public class InMemoryAlphaVantageClient implements AlphaVantageClient
{
    private final Map<String, Map<String, IncomeStatementEntry>> incomeStatements;
    private final Map<String, Map<String, CashFlowEntry>> cashFlows;

    @Inject
    public InMemoryAlphaVantageClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "alphavantage.data.file") String dataFile)
    {
        AlphaVantageData data = load(objectMapper, dataFile);
        this.incomeStatements = data.incomeStatements() == null ? Map.of() : data.incomeStatements();
        this.cashFlows = data.cashFlows() == null ? Map.of() : data.cashFlows();
    }

    @Override
    public Optional<AlphaVantageIncomeStatement> getIncomeStatement(
            String ticker,
            String periodName,
            String endingMonth)
    {
        IncomeStatementEntry entry = incomeStatements
                .getOrDefault(normalize(ticker), Map.of())
                .get(periodName);
        if (entry == null || !Objects.equals(entry.endingMonth(), endingMonth)) return Optional.empty();
        return Optional.of(entry.toDto());
    }

    @Override
    public Optional<AlphaVantageCashFlow> getCashFlow(
            String ticker,
            String periodName,
            String endingMonth)
    {
        CashFlowEntry entry = cashFlows
                .getOrDefault(normalize(ticker), Map.of())
                .get(periodName);
        if (entry == null || !Objects.equals(entry.endingMonth(), endingMonth)) return Optional.empty();
        return Optional.of(entry.toDto());
    }

    private AlphaVantageData load(ObjectMapper objectMapper, String dataFile)
    {
        Path path = Path.of(dataFile).toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(path)) {
            return objectMapper.readValue(input, AlphaVantageData.class);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to initialize fake Alpha Vantage client from '" + path + "'",
                    exception);
        }
    }

    private String normalize(String ticker)
    {
        return ticker.toUpperCase(Locale.ROOT);
    }

    private record AlphaVantageData(
            Map<String, Map<String, IncomeStatementEntry>> incomeStatements,
            Map<String, Map<String, CashFlowEntry>> cashFlows)
    {
    }

    private record IncomeStatementEntry(
            String endingMonth,
            BigDecimal revenue,
            BigDecimal grossProfit,
            BigDecimal operatingIncome,
            BigDecimal netIncome)
    {
        private AlphaVantageIncomeStatement toDto()
        {
            return new AlphaVantageIncomeStatement(revenue, grossProfit, operatingIncome, netIncome);
        }
    }

    private record CashFlowEntry(
            String endingMonth,
            BigDecimal dividend,
            BigDecimal capex,
            BigDecimal freeCashFlow)
    {
        private AlphaVantageCashFlow toDto()
        {
            return new AlphaVantageCashFlow(dividend, capex, freeCashFlow);
        }
    }
}
