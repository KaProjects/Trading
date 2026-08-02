package org.kaleta.client;

import org.kaleta.client.dto.FinnhubQuote;

public interface FinnhubClient
{
    FinnhubQuote quote(String ticker) throws RequestFailureException;
}
