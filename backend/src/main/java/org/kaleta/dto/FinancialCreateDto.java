package org.kaleta.dto;

import lombok.Data;

@Data
public class FinancialCreateDto
{
    private String companyId;
    private String quarter;
    private String revenue;
    private String costGoodsSold;
    private String operatingExpenses;
    private String netIncome;
}
