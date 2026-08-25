package org.kaleta.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AlphaVantageQuote(BigDecimal price, LocalDate date)
{
}
