package org.kaleta.rest.dto;

import lombok.Data;

@Data
public class PeriodImportCandidateDto
{
    private String name;
    private String endingMonth;
    private Boolean isReported;
}
