package org.kaleta.rest;

import jakarta.ws.rs.core.Response;
import org.kaleta.entity.Currency;

public class ParameterValidator
{
    public static void validateTicker(String ticker)
    {
        if (ticker == null || ticker.isBlank() || ticker.length() > 5 || !ticker.equals(ticker.toUpperCase())){
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Ticker Parameter: '" + ticker + "'");
        }
    }

    public static void validateCurrency(String currency)
    {
        try {
            if (currency == null || currency.isBlank()) throw new IllegalArgumentException("");
            Currency.valueOf(currency);
        } catch (IllegalArgumentException e){
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Currency Parameter: '" + currency + "'");
        }
    }

    public static void validateYear(String year)
    {
        if (year == null || year.isBlank() || !year.matches("\\d\\d\\d\\d")){
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Year Parameter: '" + year + "'");
        }
    }
}
