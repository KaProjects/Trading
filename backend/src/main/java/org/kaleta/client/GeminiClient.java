package org.kaleta.client;

import org.kaleta.client.dto.GeminiFinancials;
import org.kaleta.client.dto.GeminiTargets;

import java.time.LocalDate;
import java.util.List;

public interface GeminiClient
{
    GeminiTargets getCompanyTargets(
            String ticker,
            List<String> institutions,
            LocalDate from,
            LocalDate to) throws RequestFailureException;

    GeminiFinancials getCompanyFinancials(String ticker, int quarters) throws RequestFailureException;
}
