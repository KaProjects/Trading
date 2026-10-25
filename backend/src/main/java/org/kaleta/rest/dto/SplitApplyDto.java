package org.kaleta.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidId;

import java.math.BigDecimal;

@Data
public class SplitApplyDto
{
    @NotNull
    @ValidId
    private Long companyId;

    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
    private BigDecimal splitFrom;

    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
    private BigDecimal splitTo;

    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String date;
}
