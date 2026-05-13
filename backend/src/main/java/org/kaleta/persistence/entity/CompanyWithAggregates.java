package org.kaleta.persistence.entity;

import lombok.Data;

@Data
public class CompanyWithAggregates
{
    private String id;
    private String ticker;
    private Currency currency;
    private Boolean watching;
    private Sector sector;
    private Integer totalTrades;
    private Integer activeTrades;
    private Integer dividends;
    private Integer records;
    private Integer periods;
}
