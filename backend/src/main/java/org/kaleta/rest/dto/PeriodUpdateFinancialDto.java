package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValidUuid;

@Data
public class PeriodUpdateFinancialDto
{
    @NotNull
    @ValidUuid
    private String id;

    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String reportDate;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String shares;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String priceLow;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String priceHigh;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String revenue;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String grossProfit;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String operatingIncome;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String netIncome;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String dividend;
}
