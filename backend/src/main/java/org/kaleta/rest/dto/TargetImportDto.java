package org.kaleta.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class TargetImportDto
{
    @NotNull
    @Valid
    private List<Selection> selected = new ArrayList<>();

    @NotNull
    @Valid
    private List<Selection> discarded = new ArrayList<>();

    @Data
    public static class Selection
    {
        @NotNull
        @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
        private String date;

        @NotBlank
        @Size(max = 50)
        private String institution;

        @NotNull
        private BigDecimal price;
    }
}
