package org.kaleta.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Asset
{
    private BigDecimal quantity;
    private BigDecimal purchasePrice;
    private BigDecimal currentPrice;
    private BigDecimal profitPercent;
    private BigDecimal profitValue;
}
