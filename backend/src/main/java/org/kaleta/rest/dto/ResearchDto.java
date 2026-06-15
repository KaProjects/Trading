package org.kaleta.rest.dto;

import lombok.Data;
import org.kaleta.model.Assets;
import org.kaleta.model.Company;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;
import org.kaleta.model.Record;
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
    private List<Record> records = new ArrayList<>();
    private Latest latest;
    private PriceIndicators indicators;
    private Assets assets;
    private List<PeriodImportDto> newerCachedPeriods;

    @Data
    public static class PeriodDto extends Periods.Period {
        private PeriodImportDto cachedData;
    }

    public void addPeriod(Periods.Period period, PeriodImportDto cachedData)
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
        dto.setResearch(period.getResearch());
        dto.setFinancial(period.getFinancial());
        dto.setCachedData(cachedData);
        periods.add(dto);
    }
}
