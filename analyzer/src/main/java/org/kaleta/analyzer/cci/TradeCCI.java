package org.kaleta.analyzer.cci;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeCCI {
    private BigDecimal buyValue;
    private String buyDate;
    private BigDecimal buyCondition;
    private BigDecimal buySignal;

    private BigDecimal sellValue;
    private String sellDate;
    private BigDecimal sellCondition;
    private BigDecimal sellSignal;
}
