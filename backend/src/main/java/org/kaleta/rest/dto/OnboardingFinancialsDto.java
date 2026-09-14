package org.kaleta.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record OnboardingFinancialsDto(
        String ticker,
        List<Quarter> quarters,
        List<String> notes,
        List<String> warnings)
{
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
