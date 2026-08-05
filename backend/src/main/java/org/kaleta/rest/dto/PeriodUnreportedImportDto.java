package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidPeriodName;
import org.kaleta.rest.validation.ValidId;

@Data
public class PeriodUnreportedImportDto
{
    @NotNull
    @ValidId
    private Long companyId;

    @NotNull
    @ValidPeriodName
    private String name;

    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d$", message = "must match YYYY-MM")
    private String endingMonth;
}
