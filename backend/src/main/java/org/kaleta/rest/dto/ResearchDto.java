package org.kaleta.rest.dto;

import lombok.Data;
import org.kaleta.model.Assets;
import org.kaleta.model.Company;
import org.kaleta.model.EstimateOverview;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;
import org.kaleta.model.Record;
import org.kaleta.model.TargetStats;
import org.kaleta.persistence.entity.Latest;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResearchDto
{
    private Company company;
    private List<PeriodDto> periods = new ArrayList<>();
    private List<Periods.Financial> financials = new ArrayList<>();
    private Periods.Financial ttm;
    private EstimateOverview estimateOverview;
    private EstimateOverview revenueEstimateOverview;
    private List<Record> records = new ArrayList<>();
    /** The cases that cover this company, for reading one from the company it is about. */
    private List<CaseDto.Reference> cases = new ArrayList<>();
    private Latest latest;
    private PriceIndicators indicators;
    private Assets assets;
    private List<PeriodImportCandidateDto> importablePeriods = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class PeriodDto extends Periods.Period {
        /** Every estimate taken during the period, newest first. */
        private List<PeriodEstimates> estimates = new ArrayList<>();
        private List<PeriodEstimates> revenueEstimates = new ArrayList<>();
        private TargetStats targetStats = TargetStats.empty();
        /** The period's research, one entry per date, oldest first. */
        private List<PeriodContentDto> contents = new ArrayList<>();
    }

    public void addPeriod(
            Periods.Period period,
            List<PeriodEstimates> estimates,
            List<PeriodEstimates> revenueEstimates,
            TargetStats targetStats,
            List<PeriodContentDto> contents)
    {
        PeriodDto dto = new PeriodDto();
        dto.setId(period.getId());
        dto.setName(period.getName());
        dto.setEndingMonth(period.getEndingMonth());
        dto.setReportDate(period.getReportDate());
        dto.setPreviousReportDate(period.getPreviousReportDate());
        dto.setShares(period.getShares());
        dto.setPriceLow(period.getPriceLow());
        dto.setPriceHigh(period.getPriceHigh());
        dto.setFinancial(period.getFinancial());
        dto.setExpectedReportDate(period.getExpectedReportDate());
        dto.setReportedInFirebase(period.getReportedInFirebase());
        dto.setEstimates(estimates);
        dto.setRevenueEstimates(revenueEstimates);
        dto.setTargetStats(targetStats);
        dto.setContents(contents);
        periods.add(dto);
    }
}
