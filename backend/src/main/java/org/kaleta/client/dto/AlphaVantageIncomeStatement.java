package org.kaleta.client.dto;

import java.math.BigDecimal;

public record AlphaVantageIncomeStatement(
        BigDecimal revenue,
        BigDecimal grossProfit,
        BigDecimal operatingIncome,
        BigDecimal netIncome)
{
}
