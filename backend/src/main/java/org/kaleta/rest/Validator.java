package org.kaleta.rest;

import org.kaleta.Utils;
import org.kaleta.dto.CompanyDto;
import org.kaleta.dto.DividendCreateDto;
import org.kaleta.dto.FinancialCreateDto;
import org.kaleta.rest.dto.RecordCreateDto;
import org.kaleta.dto.RecordDto;
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeSellDto;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.persistence.entity.Sort;

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

    public static void validateCompanyAggregateSort(String sort)
    {
        try {
            if (sort == null || sort.isBlank()) throw new IllegalArgumentException("");
            Sort.CompanyAggregate.valueOf(sort);
        } catch (IllegalArgumentException e){
            throw new ValidationFailedException("Invalid Company Aggregate Sort Parameter: '" + sort + "'");
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

    public static void validateTicker(String ticker)
    {
        if (ticker == null || ticker.isBlank() || ticker.length() > 5 || !ticker.toUpperCase().equals(ticker))
            throw new ValidationFailedException("Invalid Ticker Parameter: '" + ticker + "'");
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

    public static void validateCreateFinancialDto(FinancialCreateDto dto)
    {
        if (dto.getQuarter() == null || dto.getQuarter().isBlank()
                || dto.getQuarter().length() != 4 || !dto.getQuarter().matches("\\d\\d(Q1|Q2|Q3|Q4|H1|H2|FY)"))
            throw new ValidationFailedException("Invalid Quarter: '" + dto.getQuarter() + "'");

        if (dto.getRevenue() == null || !isBigDecimal(dto.getRevenue(), 8, 2))
            throw new ValidationFailedException("Invalid Revenue: '" + dto.getRevenue() + "'");

        if (dto.getCostGoodsSold() == null || !isBigDecimal(dto.getCostGoodsSold(), 8, 2))
            throw new ValidationFailedException("Invalid Cost of Goods Sold: '" + dto.getCostGoodsSold() + "'");

        if (dto.getOperatingExpenses() == null || !isBigDecimal(dto.getOperatingExpenses(), 8, 2))
            throw new ValidationFailedException("Invalid Operating Expenses: '" + dto.getOperatingExpenses() + "'");

        if (dto.getNetIncome() == null || !isBigDecimal(dto.getNetIncome(), 8, 2))
            throw new ValidationFailedException("Invalid Net Income: '" + dto.getNetIncome() + "'");

        validateUuid(dto.getCompanyId());
    }

    public static void validateCreateEditCompanyDto(CompanyDto dto, boolean isCreate)
    {
        if (dto.getCurrency() == null)
            throw new ValidationFailedException("Missing Currency Parameter");

        if (dto.getWatching() == null)
            throw new ValidationFailedException("Missing Watching Parameter");

        if (dto.getSector() != null) Validator.validateSector(dto.getSector().getKey());

        if (dto.getShares() != null) {
            String shares = dto.getShares();
            if (shares.isBlank() || shares.length() > 7
                    || (!shares.toUpperCase().endsWith("M") && !shares.toUpperCase().endsWith("B"))
                    || !isBigDecimal(shares.substring(0, shares.length() - 1), 5, 2))
                throw new ValidationFailedException("Invalid Shares Parameter: '" + shares + "'");
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
