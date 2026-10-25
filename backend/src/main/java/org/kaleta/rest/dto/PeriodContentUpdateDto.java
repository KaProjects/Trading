package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** The date identifies the entry, so this both creates and updates one. */
@Data
public class PeriodContentUpdateDto
{
    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String date;

    private String content;
}
