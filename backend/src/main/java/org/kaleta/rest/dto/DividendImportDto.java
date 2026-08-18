package org.kaleta.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;

import java.util.ArrayList;
import java.util.List;

@Data
public class DividendImportDto
{
    @NotNull
    @Size(min = 1, max = 1000)
    private List<@Valid Row> rows = new ArrayList<>();

    @Data
    public static class Row
    {
        @NotNull
        private Integer rowNumber;
        @NotNull
        @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
        private String date;
        @NotBlank
        private String ticker;
        @NotNull
        @ValidBigDecimal(integerConstraint = 5, decimalConstraint = 2)
        private String dividend;
        @NotNull
        @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 2)
        private String tax;
    }
}
