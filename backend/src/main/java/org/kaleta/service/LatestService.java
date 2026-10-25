package org.kaleta.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.client.FinnhubClient;
import org.kaleta.client.AlphaVantageClient;
import org.kaleta.client.RequestFailureException;
import org.kaleta.client.dto.AlphaVantageQuote;
import org.kaleta.client.dto.FinnhubQuote;
import org.kaleta.persistence.api.LatestDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Latest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class LatestService
{
    public record SyncResult(Latest latest, List<String> warnings)
    {
        public SyncResult
        {
            warnings = List.copyOf(warnings);
        }
    }

    @Inject
    LatestDao latestDao;
    @Inject
    FinnhubClient finnhubClient;
    @Inject
    AlphaVantageClient alphaVantageClient;
    @Inject
    CompanyService companyService;

    public SyncResult getSyncedForWithWarnings(Long companyId)
    {
        Company company = companyService.findEntity(companyId);
        List<String> warnings = new ArrayList<>();

        Quote latestQuote = null;
        if (company.getCurrency().equals(Currency.$)){
            try {
                FinnhubQuote quote = finnhubClient.quote(company.getTicker());
                if (quote != null && !(quote.getC().equals("0") ||  quote.getT().equals("0"))) {
                    latestQuote = new Quote(
                            new BigDecimal(quote.getC()),
                            Instant.ofEpochSecond(Long.parseLong(quote.getT()))
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime());
                }
            } catch (RequestFailureException exception){
                String warning = ExternalWarnings.unavailable(
                        "Finnhub quote for " + company.getTicker(),
                        exception);
                Log.warn(warning, exception);
                warnings.add(warning);
            }
        } else if (company.getAlphaVantageTicker() != null
                && !company.getAlphaVantageTicker().isBlank()) {
            try {
                Optional<AlphaVantageQuote> quote = alphaVantageClient
                        .getQuote(company.getAlphaVantageTicker());
                if (quote.isPresent()) {
                    latestQuote = new Quote(
                            quote.get().price(),
                            quote.get().date().atStartOfDay());
                }
            } catch (RequestFailureException exception) {
                String warning = ExternalWarnings.unavailable(
                        "Alpha Vantage quote for " + company.getAlphaVantageTicker(),
                        exception);
                Log.warn(warning, exception);
                warnings.add(warning);
            }
        }

        List<Latest> latests = latestDao.list(company.getId());
        if (latests.size() > 1) {
            throw new IllegalStateException("More than one latest found for the company with id: " + company.getId());
        }

        if (latestQuote != null) {
            Latest latest;
            if (latests.isEmpty()){
                latest = new Latest();
                latest.setCompany(company);
                latest.setDatetime(latestQuote.datetime());
                latest.setPrice(latestQuote.price());
                latestDao.create(latest);
            } else {
                latest = latests.get(0);
                latest.setPrice(latestQuote.price());
                latest.setDatetime(latestQuote.datetime());
                latestDao.save(latest);
            }
            return new SyncResult(latest, warnings);
        } else {
            if (latests.isEmpty()) {
                return new SyncResult(null, warnings);
            } else {
                return new SyncResult(latests.get(0), warnings);
            }
        }
    }

    private record Quote(BigDecimal price, LocalDateTime datetime)
    {
    }
}
