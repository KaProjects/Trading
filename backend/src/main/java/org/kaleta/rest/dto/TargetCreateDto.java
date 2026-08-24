package org.kaleta.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;

@Data
public class TargetCreateDto
{
    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String date;

    @NotBlank
    @Size(max = 50)
    private String institution;

    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    @DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
    private String price;

    @Size(max = 30)
    private String rating;

    @Size(max = 1000)
    private String overview;

    @Size(max = 500)
    private String takeaway1;

    @Size(max = 500)
    private String takeaway2;

    @Size(max = 500)
    private String takeaway3;

    @Size(max = 500)
    private String takeaway4;
}
