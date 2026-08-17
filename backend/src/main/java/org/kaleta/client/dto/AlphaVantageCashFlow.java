package org.kaleta.client.dto;

import java.math.BigDecimal;

public record AlphaVantageCashFlow(
        BigDecimal dividend,
        BigDecimal capex,
        BigDecimal freeCashFlow)
{
}
