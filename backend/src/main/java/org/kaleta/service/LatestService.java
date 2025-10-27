package org.kaleta.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.client.FinnhubClient;
import org.kaleta.client.RequestFailureException;
import org.kaleta.client.dto.FinnhubQuote;
import org.kaleta.dto.LatestUiDto;
import org.kaleta.persistence.api.LatestDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.Period;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@ApplicationScoped
public class LatestService
{
    @Inject
    LatestDao latestDao;
    @Inject
    FinnhubClient finnhubClient;

    public Latest getSyncedFor(Company company)
    {
        FinnhubQuote finnhubQuote = null;
        try {
            finnhubQuote = finnhubClient.quote(company.getTicker());
        } catch (RequestFailureException e){
            Log.info(e);
        }

        List<Latest> latests = latestDao.list(company.getId());
        if (latests.size() > 1) {
            throw new ServiceFailureException("More than one latest found for the company with id: " + company.getId());
        }

        if (finnhubQuote != null) {
            LocalDateTime datetime = Instant.ofEpochSecond(Long.parseLong(finnhubQuote.getT()))
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            BigDecimal price = new BigDecimal(finnhubQuote.getC());

            Latest latest;
            if (latests.isEmpty()){
                latest = new Latest();
                latest.setCompany(company);
                latest.setDatetime(datetime);
                latest.setPrice(price);
                latestDao.create(latest);
            } else {
                latest = latests.get(0);
                latest.setPrice(price);
                latest.setDatetime(datetime);
                latestDao.save(latest);
            }
            return latest;
        } else {
            if (latests.isEmpty()) {
                return null;
            } else {
                return latests.get(0);
            }
        }
    }
}
