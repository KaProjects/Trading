package org.kaleta.dto;

import lombok.Data;

@Data
public class TradeCreateDto
{
    private String companyId;
    private String date;
    private String quantity;
    private String price;
    private String fees;
}
