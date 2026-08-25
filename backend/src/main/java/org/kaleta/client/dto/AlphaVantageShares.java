package org.kaleta.client.dto;

import java.math.BigDecimal;

public record AlphaVantageShares(BigDecimal shares, String reportedCurrency)
{
}
