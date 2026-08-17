package org.kaleta.rest.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PeriodImportDataDto
{
    private String name;
    private String endingMonth;
    private String reportDate;
    private Boolean isReported;
    private Source firebase = new Source();
    private Source polygon = new Source();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class Source
    {
        private String shares;
        private String priceLow;
        private String priceHigh;
        private String revenue;
        private String grossProfit;
        private String operatingIncome;
        private String netIncome;
        private String dividend;
        private String capex;
        private String freeCashFlow;
        private String adjustedEps;
    }
}
