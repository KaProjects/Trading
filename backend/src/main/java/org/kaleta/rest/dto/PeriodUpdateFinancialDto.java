package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValidId;

@Data
public class PeriodUpdateFinancialDto
{
    @NotNull
    @ValidId
    private Long id;

    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String reportDate;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String shares;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String priceLow;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String priceHigh;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String revenue;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String grossProfit;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String operatingIncome;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String netIncome;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String dividend;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String capex;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String freeCashFlow;

    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String adjustedEps;
}
