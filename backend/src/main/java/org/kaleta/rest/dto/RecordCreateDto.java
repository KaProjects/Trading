package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValidId;

@Data
public class RecordCreateDto
{
    @NotNull
    @ValidId
    private Long companyId;

    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String date;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String price;

    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2)
    private String priceToRevenues;

    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String priceToGrossProfit;

    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String priceToOperatingIncome;

    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String priceToNetIncome;

    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String priceToFreeCashFlow;

    @ValidBigDecimal(integerConstraint = 3, decimalConstraint = 2)
    private String dividendYield;

    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 4)
    private String sumAssetQuantity;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String avgAssetPrice;

    private String targets;
}
