package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.kaleta.persistence.entity.Currency;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/** A case as the app reads it: its own fields, plus the companies it covers by ticker. */
@Data
@RegisterForReflection
public class CaseDto
{
    private Long id;
    private String name;
    private Date createdAt;
    private Date closedAt;
    /**
     * The companies the case covers, oldest in the case first. One the app does not hold has no
     * id: its ticker is the name it was written as.
     */
    private List<Company> companies = new ArrayList<>();
    /** The days written into the case, oldest first. Only filled in when one case is read. */
    private List<CaseEntryDto> entries = new ArrayList<>();
    /** The trades its companies opened while the case ran. Read-only: nothing links them by hand. */
    private List<Trade> trades = new ArrayList<>();

    @Data
    @RegisterForReflection
    public static class Company
    {
        /** Null for a company the app does not hold, whose ticker is the name written for it. */
        private Long id;
        private String ticker;
        /** The day it was taken into the case; its trades count from then. */
        private Date addedAt;
    }

    @Data
    @RegisterForReflection
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
        /**
         * What the company last traded at, for a position still open: what it is worth now is
         * the only way to say how it is doing. Null where the app holds no price for it.
         */
        private BigDecimal currentPrice;
    }
}
