package org.kaleta.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PriceIndicators
{
    private LocalDateTime datetime;
    private BigDecimal price;
    private BigDecimal shares;
    private BigDecimal marketCap;
    private Financial ttm;

    @Data
    public static class Financial {
        private BigDecimal marketCapToRevenues;
        private BigDecimal marketCapToGrossProfit;
        private BigDecimal marketCapToOperatingIncome;
        private BigDecimal marketCapToNetIncome;
        private BigDecimal dividendYield;
    }
}
