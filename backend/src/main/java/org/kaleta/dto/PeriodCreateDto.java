package org.kaleta.dto;

import lombok.Data;

@Data
public class PeriodCreateDto
{
    private String companyId;
    private String name;
    private String endingMonth;
    private String reportDate;
}
