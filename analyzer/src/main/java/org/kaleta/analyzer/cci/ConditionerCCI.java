package org.kaleta.analyzer.cci;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConditionerCCI {

    private BigDecimal buyCondition;
    private BigDecimal buySignal;
    private BigDecimal sellCondition;
    private BigDecimal sellSignal;

    public ConditionerCCI(BigDecimal buyCondition, BigDecimal buySignal, BigDecimal sellCondition, BigDecimal sellSignal) {
        this.buyCondition = buyCondition;
        this.buySignal = buySignal;
        this.sellCondition = sellCondition;
        this.sellSignal = sellSignal;
    }
}
