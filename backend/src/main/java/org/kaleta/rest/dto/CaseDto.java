package org.kaleta.rest.dto;

import lombok.Data;
import org.kaleta.persistence.entity.Currency;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/** A case as the app reads it: its own fields, plus the companies it covers by ticker. */
@Data
public class CaseDto
{
    private Long id;
    private String name;
    private Date createdAt;
    private Date closedAt;
    private List<Company> companies = new ArrayList<>();
    /** Companies the app does not hold, as the names they were written as. */
    private List<String> companyNames = new ArrayList<>();
    /** The days written into the case, oldest first. Only filled in when one case is read. */
    private List<CaseEntryDto> entries = new ArrayList<>();
    /** The trades its companies opened while the case ran. Read-only: nothing links them by hand. */
    private List<Trade> trades = new ArrayList<>();

    @Data
    public static class Company
    {
        private Long id;
        private String ticker;
    }

    @Data
    public static class Trade
    {
        private Long id;
        private String ticker;
        private Currency currency;
        private boolean active;
        private Date purchaseDate;
        private BigDecimal purchasePrice;
        /** What the purchase cost altogether, fees included. */
        private BigDecimal purchaseTotal;
        private BigDecimal quantity;
        private Date sellDate;
        private BigDecimal sellPrice;
        private BigDecimal profit;
        private BigDecimal profitPercentage;
    }
}
