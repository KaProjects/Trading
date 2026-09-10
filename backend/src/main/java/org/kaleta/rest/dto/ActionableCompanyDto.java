package org.kaleta.rest.dto;

import lombok.Data;
import org.kaleta.persistence.entity.CompanyWithStats;

@Data
public class ActionableCompanyDto
{
    private CompanyWithStats company;
    private int importablePeriodsCount;
    private int importableTargetsCount;
}
