package org.kaleta.client;

import org.kaleta.client.dto.FinnhubEarnings;
import org.kaleta.client.dto.FinnhubQuote;

import java.time.LocalDate;
import java.util.List;

public interface FinnhubClient
{
    FinnhubQuote quote(String ticker) throws RequestFailureException;

    List<FinnhubEarnings> earningsCalendar(String ticker, LocalDate from, LocalDate to)
            throws RequestFailureException;
}
