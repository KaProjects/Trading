package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValueOfEnum;

@Data
public class TradeUpdateDto
{
    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String purchaseDate;

    @NotNull
    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 4)
    private String quantity;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String purchasePrice;

    @NotNull
    @ValidBigDecimal(integerConstraint = 3, decimalConstraint = 2)
    private String purchaseFees;

    @ValueOfEnum(enumClass = Portfolio.class)
    private String portfolio;

    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String sellDate;

    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String sellPrice;

    @ValidBigDecimal(integerConstraint = 3, decimalConstraint = 2)
    private String sellFees;
}
