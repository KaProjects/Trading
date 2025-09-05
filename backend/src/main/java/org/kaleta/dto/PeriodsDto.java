package org.kaleta.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PeriodsDto
{
    private CompanyDto company;
    private List<PeriodDto> periods = new ArrayList<>();
    private List<FinancialDto> financials = new ArrayList<>();
    private FinancialDto ttm;
}
