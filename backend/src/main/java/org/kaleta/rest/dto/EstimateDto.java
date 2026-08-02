package org.kaleta.rest.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EstimateDto
{
    private Long id;
    private Long periodId;
    private LocalDateTime datetime;
    private BigDecimal current;
    private BigDecimal next1;
    private BigDecimal next2;
    private BigDecimal next3;
}
