package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.rest.validation.ValidPeriodName;
import org.kaleta.rest.validation.ValidUuid;

@Data
public class PeriodCreateDto
{
    @NotNull
    @ValidUuid
    private String companyId;
    @NotNull
    @ValidPeriodName
    private String name;
    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d$", message = "must match YYYY-MM")
    private String endingMonth;
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String reportDate;
}
