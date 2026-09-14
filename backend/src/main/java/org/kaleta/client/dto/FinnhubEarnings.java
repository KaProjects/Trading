package org.kaleta.client.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;

@RegisterForReflection
public record FinnhubEarnings(
        String date,
        Integer year,
        Integer quarter,
        String hour,
        BigDecimal epsActual,
        BigDecimal epsEstimate,
        BigDecimal revenueActual,
        BigDecimal revenueEstimate)
{
}
