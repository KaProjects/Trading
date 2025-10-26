package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValidPeriodName;
import org.kaleta.rest.validation.ValidUuid;

@Data
public class PeriodUpdateDto
{
    @NotNull
    @ValidUuid
    private String id;
    @ValidPeriodName
    private String name;
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d$", message = "must match YYYY-MM")
    private String endingMonth;
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String reportDate;
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String shares;
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String priceLow;
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String priceHigh;
    private String research;
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String revenue;
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String costGoodsSold;
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String operatingExpenses;
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String netIncome;
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String dividend;
}
