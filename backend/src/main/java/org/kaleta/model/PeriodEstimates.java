package org.kaleta.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PeriodEstimates
{
    private Long id;
    private Long periodId;
    private LocalDateTime datetime;
    private BigDecimal current;
    private BigDecimal next1;
    private BigDecimal next2;
    private BigDecimal next3;
    private BigDecimal past1;
    private BigDecimal past2;
    private BigDecimal past3;
    private BigDecimal past4;
    private BigDecimal pastTotal;
    private BigDecimal currentChange;
    private BigDecimal next1Change;
    private BigDecimal next2Change;
    private BigDecimal next3Change;
}
