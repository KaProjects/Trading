package org.kaleta.client;

import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonCompanyProfile;
import org.kaleta.client.dto.PolygonPriceRange;
import org.kaleta.client.dto.PolygonSplit;

import java.util.List;
import java.util.Optional;

public interface PolygonClient
{
    Optional<PolygonCompanyProfile> getCompanyProfile(String ticker) throws RequestFailureException;

    Optional<PolygonFinancials> getFinancials(
            String ticker,
            String fiscalYear,
            String fiscalPeriod) throws RequestFailureException;

    Optional<PolygonPriceRange> getPriceRange(
            String ticker,
            String from,
            String to) throws RequestFailureException;

    List<PolygonSplit> getSplits(String ticker, String executedFrom) throws RequestFailureException;
}
