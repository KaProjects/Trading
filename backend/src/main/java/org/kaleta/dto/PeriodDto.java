package org.kaleta.dto;

import lombok.Data;

@Data
public class PeriodDto
{
    private String id;
    private String name;
    private String endingMonth;
    private String reportDate;
    private String shares;
    private String priceLow;
    private String priceHigh;
    private String research;
    private String revenue;
    private String costGoodsSold;
    private String operatingExpenses;
    private String netIncome;
    private String dividend;
}
