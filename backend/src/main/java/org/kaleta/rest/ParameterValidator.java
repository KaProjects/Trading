package org.kaleta.rest;

import jakarta.ws.rs.core.Response;

public class ParameterValidator
{
    public static void validateTicker(String ticker)
    {
        if (ticker == null || ticker.isBlank() || ticker.length() > 5 || !ticker.equals(ticker.toUpperCase())){
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Ticker Parameter: '" + ticker + "'");
        }
    }
}
