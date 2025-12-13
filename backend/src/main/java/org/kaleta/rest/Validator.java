package org.kaleta.rest;

import org.kaleta.Utils;
import org.kaleta.dto.DividendCreateDto;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;

import java.math.BigDecimal;
import java.util.UUID;

@Deprecated // TODO use jakarta.validation
public class Validator
{
    public static void validatePayload(Object payload)
    {
        if (payload == null)
            throw new ValidationFailedException("Payload is NULL");
    }

    public static void validateCurrency(String currency)
    {
        try {
            if (currency == null || currency.isBlank()) throw new IllegalArgumentException("");
            Currency.valueOf(currency);
        } catch (IllegalArgumentException e){
            throw new ValidationFailedException("Invalid Currency Parameter: '" + currency + "'");
        }
    }

    public static void validateYear(String year)
    {
        if (year == null || year.isBlank() || !year.matches("\\d\\d\\d\\d")){
            throw new ValidationFailedException("Invalid Year Parameter: '" + year + "'");
        }
    }

    public static void validateUuid(String uuid)
    {
        try {
            if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("");
            UUID.fromString(uuid);
        } catch (IllegalArgumentException e){
            throw new ValidationFailedException("Invalid UUID Parameter: '" + uuid + "'");
        }
    }

    public static void validateSector(String sector)
    {
        try {
            if (sector == null || sector.isBlank()) throw new IllegalArgumentException("");
            Sector.valueOf(sector);
        } catch (IllegalArgumentException e){
            throw new ValidationFailedException("Invalid Sector Parameter: '" + sector + "'");
        }
    }

    public static void validateCreateDividendDto(DividendCreateDto dto)
    {
        if (dto.getDate() == null || !Utils.isValidDbDate(dto.getDate()))
            throw new ValidationFailedException("Invalid Date: '" + dto.getDate() + "'");

        if (dto.getDividend() == null || !isBigDecimal(dto.getDividend(), 7, 2))
            throw new ValidationFailedException("Invalid Dividend: '" + dto.getDividend() + "'");

        if (dto.getTax() == null || !isBigDecimal(dto.getTax(), 6, 2))
            throw new ValidationFailedException("Invalid Tax: '" + dto.getTax() + "'");

        validateUuid(dto.getCompanyId());
    }

    private static boolean isBigDecimal(String value, int lengthConstraint, int decimalConstraint){
        try {
            new BigDecimal(value);
            if (value.startsWith(".")) return false;
            if (value.endsWith(".")) return false;
            String[] split = value.split("\\.");
            if (split[0].length() > lengthConstraint - decimalConstraint) return false;
            if (split.length > 1 && split[1].length() > decimalConstraint) return false;
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
