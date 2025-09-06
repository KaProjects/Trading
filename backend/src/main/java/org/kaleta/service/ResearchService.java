package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dto.ResearchUiDto;
import org.kaleta.entity.Period;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ResearchService
{
    @Inject
    CompanyService companyService;
    @Inject
    PeriodService periodService;
    @Inject
    RecordService recordService;

    public ResearchUiDto getDto(String companyId)
    {
        ResearchUiDto dto = new ResearchUiDto();
        dto.setCompany(companyService.getDto(companyId));

        List<Period> periods = periodService.getBy(companyId);

        for (Period period : periods) {
            dto.getPeriods().add(periodService.dtoFrom(period));
            if (period.getRevenue() != null){
                dto.getFinancials().add(periodService.computeFinancialFrom(period));
            }
        }
        dto.setTtm(periodService.computeTtmFrom(
                periods.stream()
                        .filter(p -> p.getRevenue() != null)
                        .limit(4)
                        .collect(Collectors.toList()))
        );
        return dto;
    }
}
