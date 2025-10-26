package org.kaleta.dto;

import lombok.Data;

@Data
@Deprecated
public class FinancialDto
{
    private String period;
    private String revenue;
    private String costGoodsSold;
    private String grossProfit;
    private String grossMargin;
    private String operatingExpenses;
    private String operatingIncome;
    private String operatingMargin;
    private String netIncome;
    private String netMargin;
    private String dividend;
}
