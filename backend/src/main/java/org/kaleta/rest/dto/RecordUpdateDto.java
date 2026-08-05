package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValidId;

@Data
public class RecordUpdateDto
{
    @NotNull
    @ValidId
    private Long id;
    private String title;
    private String content;
    private String review;
    private String strategy;
    private String retro;
    private String targets;
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String price;
    @ValidBigDecimal(integerConstraint = 3, decimalConstraint = 2, allowBlank = true)
    private String dividendYield;
    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2)
    private String priceToRevenues;
    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String priceToGrossProfit;
    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String priceToOperatingIncome;
    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String priceToNetIncome;
    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 4)
    private String sumAssetQuantity;
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String avgAssetPrice;
}
