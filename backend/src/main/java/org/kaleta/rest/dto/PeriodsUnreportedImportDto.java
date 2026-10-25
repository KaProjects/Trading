package org.kaleta.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.rest.validation.ValidPeriodName;

import java.util.List;

/**
 * Several quarters imported in one go, each with nothing but its name and ending month - what a
 * company's periods look like before anything is reported for them. For a company whose financials
 * are not worth filling in one by one, this is how its calendar gets into the app at all.
 */
@Data
public class PeriodsUnreportedImportDto
{
    @NotNull
    @ValidId
    private Long companyId;

    @NotEmpty
    @Valid
    private List<Quarter> periods;

    @Data
    public static class Quarter
    {
        @NotNull
        @ValidPeriodName
        private String name;

        @NotNull
        @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d$", message = "must match YYYY-MM")
        private String endingMonth;
    }
}
