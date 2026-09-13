package org.kaleta.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class Asset
{
    private LocalDate purchaseDate;
    private BigDecimal quantity;
    private BigDecimal purchasePrice;
    private BigDecimal currentPrice;
    private BigDecimal profitPercent;
    private BigDecimal profitValue;
}
