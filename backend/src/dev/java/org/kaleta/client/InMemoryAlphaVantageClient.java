package org.kaleta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageEarnings;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;
import org.kaleta.client.dto.AlphaVantagePriceRange;
import org.kaleta.client.dto.AlphaVantageQuote;
import org.kaleta.client.dto.AlphaVantageShares;
import org.kaleta.client.dto.AlphaVantageTicker;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
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
    private final Map<String, List<AlphaVantageTicker>> tickerSearches;
    private final Map<String, QuoteEntry> quotes;
    private final Map<String, Map<String, PriceRangeEntry>> priceRanges;
    private final Map<String, Map<String, SharesEntry>> shares;
    private final Map<String, Map<String, EarningsEntry>> earnings;

    @Inject
    public InMemoryAlphaVantageClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "alphavantage.data.file") String dataFile)
    {
        AlphaVantageData data = load(objectMapper, dataFile);
        this.incomeStatements = data.incomeStatements() == null ? Map.of() : data.incomeStatements();
        this.cashFlows = data.cashFlows() == null ? Map.of() : data.cashFlows();
        this.tickerSearches = data.tickerSearches() == null ? Map.of() : data.tickerSearches();
        this.quotes = data.quotes() == null ? Map.of() : data.quotes();
        this.priceRanges = data.priceRanges() == null ? Map.of() : data.priceRanges();
        this.shares = data.shares() == null ? Map.of() : data.shares();
        this.earnings = data.earnings() == null ? Map.of() : data.earnings();
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

    @Override
    public List<AlphaVantageTicker> searchTickers(String ticker)
    {
        return List.copyOf(tickerSearches.getOrDefault(normalize(ticker), List.of()));
    }

    @Override
    public Optional<AlphaVantageQuote> getQuote(String ticker)
    {
        QuoteEntry quote = quotes.get(normalize(ticker));
        return quote == null ? Optional.empty() : Optional.of(quote.toDto());
    }

    @Override
    public Optional<AlphaVantagePriceRange> getPriceRange(String ticker, String from, String to)
    {
        PriceRangeEntry value = priceRanges
                .getOrDefault(normalize(ticker), Map.of())
                .get(rangeKey(from, to));
        return value == null ? Optional.empty() : Optional.of(value.toDto());
    }

    @Override
    public Optional<AlphaVantageShares> getShares(
            String ticker,
            String periodName,
            String endingMonth)
    {
        SharesEntry value = shares
                .getOrDefault(normalize(ticker), Map.of())
                .get(periodName);
        if (value == null || !Objects.equals(value.endingMonth(), endingMonth)) return Optional.empty();
        return Optional.of(value.toDto());
    }

    @Override
    public Optional<AlphaVantageEarnings> getEarnings(
            String ticker,
            String periodName,
            String endingMonth)
    {
        EarningsEntry value = earnings
                .getOrDefault(normalize(ticker), Map.of())
                .get(periodName);
        if (value == null || !Objects.equals(value.endingMonth(), endingMonth)) return Optional.empty();
        return Optional.of(value.toDto());
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

    private String rangeKey(String from, String to)
    {
        return from + ":" + to;
    }

    private record AlphaVantageData(
            Map<String, Map<String, IncomeStatementEntry>> incomeStatements,
            Map<String, Map<String, CashFlowEntry>> cashFlows,
            Map<String, List<AlphaVantageTicker>> tickerSearches,
            Map<String, QuoteEntry> quotes,
            Map<String, Map<String, PriceRangeEntry>> priceRanges,
            Map<String, Map<String, SharesEntry>> shares,
            Map<String, Map<String, EarningsEntry>> earnings)
    {
    }

    private record IncomeStatementEntry(
            String endingMonth,
            BigDecimal revenue,
            BigDecimal grossProfit,
            BigDecimal operatingIncome,
            BigDecimal netIncome,
            String reportedCurrency)
    {
        private AlphaVantageIncomeStatement toDto()
        {
            return new AlphaVantageIncomeStatement(
                    revenue,
                    grossProfit,
                    operatingIncome,
                    netIncome,
                    reportedCurrency);
        }
    }

    private record CashFlowEntry(
            String endingMonth,
            BigDecimal dividend,
            BigDecimal capex,
            BigDecimal freeCashFlow,
            String reportedCurrency)
    {
        private AlphaVantageCashFlow toDto()
        {
            return new AlphaVantageCashFlow(dividend, capex, freeCashFlow, reportedCurrency);
        }
    }

    private record QuoteEntry(BigDecimal price, String date)
    {
        private AlphaVantageQuote toDto()
        {
            return new AlphaVantageQuote(price, LocalDate.parse(date));
        }
    }

    private record PriceRangeEntry(BigDecimal high, BigDecimal low)
    {
        private AlphaVantagePriceRange toDto()
        {
            return new AlphaVantagePriceRange(high, low);
        }
    }

    private record SharesEntry(
            String endingMonth,
            BigDecimal shares,
            String reportedCurrency)
    {
        private AlphaVantageShares toDto()
        {
            return new AlphaVantageShares(shares, reportedCurrency);
        }
    }

    private record EarningsEntry(String endingMonth, BigDecimal reportedEps)
    {
        private AlphaVantageEarnings toDto()
        {
            return new AlphaVantageEarnings(reportedEps);
        }
    }
}
