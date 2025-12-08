package org.kaleta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValidUuid;

@Data
public class TradeCreateDto
{
    @NotNull
    @ValidUuid
    private String companyId;
    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String date;
    @NotNull
    @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 4)
    private String quantity;
    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String price;
    @NotNull
    @ValidBigDecimal(integerConstraint = 3, decimalConstraint = 2)
    private String fees;
}