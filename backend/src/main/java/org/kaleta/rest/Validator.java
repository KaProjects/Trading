package org.kaleta.rest;

import jakarta.ws.rs.core.Response;
import org.kaleta.Constants;
import org.kaleta.dto.RecordCreateDto;
import org.kaleta.dto.RecordDto;
import org.kaleta.entity.Currency;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.UUID;

public class Validator
{
    public static void validatePayload(Object payload)
    {
        if (payload == null)
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Payload is NULL");
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

    public static void validateUuid(String uuid)
    {
        try {
            if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("");
            UUID.fromString(uuid);
        } catch (IllegalArgumentException e){
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid UUID Parameter: '" + uuid + "'");
        }
    }

    public static void validateUpdateRecordDto(RecordDto dto)
    {
        if (dto.getDate() != null && !isDate(dto.getDate()))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Date: '" + dto.getDate() + "'");

        if (dto.getTitle() != null && dto.getTitle().isBlank())
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Title: '" + dto.getTitle() + "'");

        if (dto.getPrice() != null && !isBigDecimal(dto.getPrice(), 10, 4))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Price: '" + dto.getPrice() + "'");

        if (dto.getPe() != null && !dto.getPe().isBlank() && !isBigDecimal(dto.getPe(), 5, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid PE: '" + dto.getPe() + "'");

        if (dto.getDy() != null && !dto.getDy().isBlank() &&!isBigDecimal(dto.getDy(), 5, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid DY: '" + dto.getDy() + "'");
    }


    public static void validateCreateRecordDto(RecordCreateDto dto)
    {
        if (dto.getDate() == null || !isDate(dto.getDate()))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Date: '" + dto.getDate() + "'");

        if (dto.getTitle() == null || dto.getTitle().isBlank())
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Title: '" + dto.getTitle() + "'");

        if (dto.getPrice() == null || !isBigDecimal(dto.getPrice(), 10, 4))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Price: '" + dto.getPrice() + "'");

        validateUuid(dto.getCompanyId());
    }

    private static boolean isDate(String value){
        try {
            Constants.dateFormatDto.parse(value);
            return value.matches("\\d\\d\\.\\d\\d\\.\\d\\d\\d\\d");
        } catch (ParseException e) {
            return false;
        }
    }

    private static boolean isBigDecimal(String value, int lengthConstraint, int decimalConstraint){
        try {
            new BigDecimal(value);
            if (value.endsWith(".")) return false;
            if (value.replace(".", "").length() > lengthConstraint) return false;
            String[] split = value.split("\\.");
            if (split.length > 1 && split[1].length() > decimalConstraint) return false;
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
