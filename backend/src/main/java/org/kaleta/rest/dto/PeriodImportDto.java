package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValidPeriodName;
import org.kaleta.rest.validation.ValidUuid;

@Data
public class PeriodImportDto
{
    @NotNull
    @ValidUuid
    private String companyId;

    @NotNull
    @ValidPeriodName
    private String name;

    @NotNull
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

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String revenue;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String grossProfit;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String operatingIncome;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2, allowNegative = true)
    private String netIncome;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 2)
    private String dividend;

    private Boolean isReported = false;
    private String previousReportDate;
}
