package org.kaleta.rest.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EpsOutperformerDto
{
    private String ticker;
    private BigDecimal ttmEps;
    private BigDecimal quarter1Change;
    private BigDecimal quarter2Change;
    private BigDecimal quarter3Change;
    private BigDecimal quarter4Change;
}
