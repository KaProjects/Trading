package org.kaleta.rest;

import jakarta.ws.rs.core.Response;
import org.kaleta.entity.Currency;

import java.util.UUID;

public class ParameterValidator
{
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

    public static void validateUuid(String uuid)
    {
        try {
            if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("");
            UUID.fromString(uuid);
        } catch (IllegalArgumentException e){
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid UUID Parameter: '" + uuid + "'");
        }
    }
}
