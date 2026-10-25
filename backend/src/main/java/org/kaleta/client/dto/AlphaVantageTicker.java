package org.kaleta.client.dto;

import java.math.BigDecimal;

public record AlphaVantageTicker(
        String symbol,
        String name,
        String type,
        String region,
        String currency,
        BigDecimal matchScore)
{
}
