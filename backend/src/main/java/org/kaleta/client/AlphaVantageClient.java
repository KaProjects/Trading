package org.kaleta.client;

import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;

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
}
