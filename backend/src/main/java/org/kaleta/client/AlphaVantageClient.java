package org.kaleta.client;

import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageEarnings;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;
import org.kaleta.client.dto.AlphaVantagePriceRange;
import org.kaleta.client.dto.AlphaVantageQuote;
import org.kaleta.client.dto.AlphaVantageShares;
import org.kaleta.client.dto.AlphaVantageTicker;

import java.util.List;
import java.util.Optional;

public interface AlphaVantageClient
{
    Optional<AlphaVantageIncomeStatement> getIncomeStatement(
            String ticker,
            String periodName,
            String endingMonth) throws RequestFailureException;

    Optional<AlphaVantageCashFlow> getCashFlow(
            String ticker,
            String periodName,
            String endingMonth) throws RequestFailureException;

    List<AlphaVantageTicker> searchTickers(String ticker) throws RequestFailureException;

    Optional<AlphaVantageQuote> getQuote(String ticker) throws RequestFailureException;

    Optional<AlphaVantagePriceRange> getPriceRange(
            String ticker,
            String from,
            String to) throws RequestFailureException;

    Optional<AlphaVantageShares> getShares(
            String ticker,
            String periodName,
            String endingMonth) throws RequestFailureException;

    Optional<AlphaVantageEarnings> getEarnings(
            String ticker,
            String periodName,
            String endingMonth) throws RequestFailureException;
}
