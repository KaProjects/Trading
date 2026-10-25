package org.kaleta.rest.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MarginOutperformerDto
{
    private String ticker;
    private BigDecimal revenue;
    private BigDecimal grossMargin;
    private BigDecimal operatingMargin;
    private BigDecimal netMargin;
}
