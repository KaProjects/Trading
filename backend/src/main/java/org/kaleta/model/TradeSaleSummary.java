package org.kaleta.model;

import java.math.BigDecimal;

public record TradeSaleSummary(
        BigDecimal quantity,
        BigDecimal averagePurchasePrice,
        BigDecimal fees,
        BigDecimal profit,
        BigDecimal profitPercentage)
{
}
