package org.kaleta.client.dto;

import java.math.BigDecimal;

public record PolygonFinancials(
        BigDecimal shares,
        BigDecimal revenue,
        BigDecimal grossProfit,
        BigDecimal operatingIncome,
        BigDecimal netIncome,
        String reportedCurrency)
{
}
