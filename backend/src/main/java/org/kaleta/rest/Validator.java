package org.kaleta.rest;

import jakarta.ws.rs.core.Response;
import org.kaleta.Utils;
import org.kaleta.dto.RecordCreateDto;
import org.kaleta.dto.RecordDto;
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeSellDto;
import org.kaleta.entity.Currency;

import java.math.BigDecimal;
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
        if (dto.getDate() != null && !Utils.isValidDbDate(dto.getDate()))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Date: '" + dto.getDate() + "'");

        if (dto.getTitle() != null && dto.getTitle().isBlank())
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Title: '" + dto.getTitle() + "'");

        if (dto.getPrice() != null && !isBigDecimal(dto.getPrice(), 10, 4))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Price: '" + dto.getPrice() + "'");

        if (dto.getPe() != null && !dto.getPe().isBlank() && !isBigDecimal(dto.getPe(), 5, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid PE: '" + dto.getPe() + "'");

        if (dto.getPs() != null && !dto.getPs().isBlank() && !isBigDecimal(dto.getPs(), 5, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid PS: '" + dto.getPe() + "'");

        if (dto.getDy() != null && !dto.getDy().isBlank() &&!isBigDecimal(dto.getDy(), 5, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid DY: '" + dto.getDy() + "'");
    }


    public static void validateCreateRecordDto(RecordCreateDto dto)
    {
        if (dto.getDate() == null || !Utils.isValidDbDate(dto.getDate()))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Date: '" + dto.getDate() + "'");

        if (dto.getTitle() == null || dto.getTitle().isBlank())
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Title: '" + dto.getTitle() + "'");

        if (dto.getPrice() == null || !isBigDecimal(dto.getPrice(), 10, 4))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Price: '" + dto.getPrice() + "'");

        validateUuid(dto.getCompanyId());
    }

    public static void validateCreateTradeDto(TradeCreateDto dto)
    {
        if (dto.getDate() == null || !Utils.isValidDbDate(dto.getDate()))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Date: '" + dto.getDate() + "'");

        if (dto.getQuantity() == null || !isBigDecimal(dto.getQuantity(), 8, 4))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Quantity: '" + dto.getQuantity() + "'");

        if (dto.getPrice() == null || !isBigDecimal(dto.getPrice(), 10, 4))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Price: '" + dto.getPrice() + "'");

        if (dto.getFees() == null || !isBigDecimal(dto.getFees(), 5, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Fees: '" + dto.getFees() + "'");

        validateUuid(dto.getCompanyId());
    }

    public static void validateSellTradeDto(TradeSellDto dto)
    {
        if (dto.getDate() == null || !Utils.isValidDbDate(dto.getDate()))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Date: '" + dto.getDate() + "'");

        if (dto.getPrice() == null || !isBigDecimal(dto.getPrice(), 10, 4))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Price: '" + dto.getPrice() + "'");

        if (dto.getFees() == null || !isBigDecimal(dto.getFees(), 5, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Fees: '" + dto.getFees() + "'");

        if (dto.getTrades().size() == 0)
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "No trades to sell provided");

        for (TradeSellDto.Trade trade : dto.getTrades())
        {
            if (trade.getQuantity() == null || !isBigDecimal(trade.getQuantity(), 8, 4))
                throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Quantity: '" + trade.getQuantity() + "'");

            validateUuid(trade.getTradeId());
        }
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
