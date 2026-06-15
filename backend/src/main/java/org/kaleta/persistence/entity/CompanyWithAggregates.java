package org.kaleta.persistence.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyWithAggregates extends Company
{
    private Integer totalTrades;
    private Integer activeTrades;
    private Integer dividends;
    private Integer records;
    private Integer periods;
}
