package org.kaleta.rest.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TargetOutperformerDto
{
    private String ticker;
    private BigDecimal price;
    private BigDecimal averageTarget;
    private BigDecimal percentDiff;
}
