package org.kaleta.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record OnboardingEstimatesDto(
        String ticker,
        List<Quarter> reported,
        List<Quarter> estimated,
        Projection projection,
        List<String> warnings)
{
    public record Quarter(
            String label,
            String date,
            BigDecimal eps,
            BigDecimal revenue)
    {
    }

    public record Projection(
            Window ttm,
            Window current,
            Window next1,
            Window next2,
            Window next3)
    {
    }

    public record Window(
            BigDecimal eps,
            BigDecimal change)
    {
    }
}
