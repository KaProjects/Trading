package org.kaleta.client.dto;

import java.math.BigDecimal;

public record PolygonPriceRange(BigDecimal high, BigDecimal low, String reportedCurrency)
{
}
