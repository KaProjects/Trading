package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;

@Data
public class EstimateCreateDto
{
    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String date;

    @NotNull
    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String current;

    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String next1;

    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String next2;

    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2, allowNegative = true)
    private String next3;
}
