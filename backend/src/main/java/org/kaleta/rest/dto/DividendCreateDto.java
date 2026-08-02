package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValidId;

@Data
public class DividendCreateDto
{
    @NotNull
    @ValidId
    private Long companyId;
    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String date;
    @NotNull
    @ValidBigDecimal(integerConstraint = 5, decimalConstraint = 2)
    private String dividend;
    @NotNull
    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2)
    private String tax;
}
