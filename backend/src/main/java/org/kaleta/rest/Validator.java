package org.kaleta.rest;

import jakarta.ws.rs.core.Response;
import org.kaleta.Utils;
import org.kaleta.dto.CompanyDto;
import org.kaleta.dto.DividendCreateDto;
import org.kaleta.dto.FinancialCreateDto;
import org.kaleta.dto.RecordCreateDto;
import org.kaleta.dto.RecordDto;
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeSellDto;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Sector;
import org.kaleta.entity.Sort;

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

    public static void validateCompanyAggregateSort(String sort)
    {
        try {
            if (sort == null || sort.isBlank()) throw new IllegalArgumentException("");
            Sort.CompanyAggregate.valueOf(sort);
        } catch (IllegalArgumentException e){
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Company Aggregate Sort Parameter: '" + sort + "'");
        }
    }

    public static void validateSector(String sector)
    {
        try {
            if (sector == null || sector.isBlank()) throw new IllegalArgumentException("");
            Sector.valueOf(sector);
        } catch (IllegalArgumentException e){
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Sector Parameter: '" + sector + "'");
        }
    }

    public static void validateTicker(String ticker)
    {
        if (ticker == null || ticker.isBlank() || ticker.length() > 5 || !ticker.toUpperCase().equals(ticker))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Ticker Parameter: '" + ticker + "'");
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

    public static void validateCreateDividendDto(DividendCreateDto dto)
    {
        if (dto.getDate() == null || !Utils.isValidDbDate(dto.getDate()))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Date: '" + dto.getDate() + "'");

        if (dto.getDividend() == null || !isBigDecimal(dto.getDividend(), 7, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Dividend: '" + dto.getDividend() + "'");

        if (dto.getTax() == null || !isBigDecimal(dto.getTax(), 6, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Tax: '" + dto.getTax() + "'");

        validateUuid(dto.getCompanyId());
    }

    public static void validateCreateFinancialDto(FinancialCreateDto dto)
    {
        if (dto.getQuarter() == null || dto.getQuarter().isBlank()
                || dto.getQuarter().length() != 4 || !dto.getQuarter().matches("\\d\\d(Q1|Q2|Q3|Q4|H1|H2|FY)"))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Quarter: '" + dto.getQuarter() + "'");

        if (dto.getRevenue() == null || !isBigDecimal(dto.getRevenue(), 8, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Revenue: '" + dto.getRevenue() + "'");

        if (dto.getCostGoodsSold() == null || !isBigDecimal(dto.getCostGoodsSold(), 8, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Cost of Goods Sold: '" + dto.getCostGoodsSold() + "'");

        if (dto.getOperatingExpenses() == null || !isBigDecimal(dto.getOperatingExpenses(), 8, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Operating Expenses: '" + dto.getOperatingExpenses() + "'");

        if (dto.getNetIncome() == null || !isBigDecimal(dto.getNetIncome(), 8, 2))
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Net Income: '" + dto.getNetIncome() + "'");

        validateUuid(dto.getCompanyId());
    }

    public static void validateCreateEditCompanyDto(CompanyDto dto, boolean isCreate)
    {
        if (dto.getCurrency() == null)
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Missing Currency Parameter");

        if (dto.getWatching() == null)
            throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Missing Watching Parameter");

        if (dto.getSector() != null) Validator.validateSector(dto.getSector().getKey());

        if (dto.getShares() != null) {
            String shares = dto.getShares();
            if (shares.isBlank() || shares.length() > 7
                    || (!shares.toUpperCase().endsWith("M") && !shares.toUpperCase().endsWith("B"))
                    || !isBigDecimal(shares.substring(0, shares.length() - 1), 5, 2))
                throw new ResponseStatusException(Response.Status.BAD_REQUEST, "Invalid Shares Parameter: '" + shares + "'");
        }

        if (isCreate)
            validateTicker(dto.getTicker());
        else
            validateUuid(dto.getId());
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
