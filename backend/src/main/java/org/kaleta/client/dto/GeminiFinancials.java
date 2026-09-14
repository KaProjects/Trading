package org.kaleta.client.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.List;

@RegisterForReflection
public record GeminiFinancials(
        List<Quarter> quarters,
        List<String> notes)
{
    @RegisterForReflection
    public record Quarter(
            String id,
            String name,
            String endingMonth,
            String reportDate,
            BigDecimal revenue,
            BigDecimal grossProfit,
            BigDecimal operatingIncome,
            BigDecimal netIncome,
            BigDecimal capex,
            BigDecimal freeCashFlow,
            BigDecimal dividend,
            BigDecimal shares,
            BigDecimal adjustedEps,
            BigDecimal priceMin,
            BigDecimal priceMax)
    {
    }
}
