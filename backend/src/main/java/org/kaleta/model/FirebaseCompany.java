package org.kaleta.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.PeriodImportCandidateDto;
import org.kaleta.rest.dto.PeriodImportDto;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Data
@RegisterForReflection
public class FirebaseCompany
{
    private Map<String, Map<String, FinnhubEarnings>> fhe;
    private Gemini gemini;

    @Data
    @RegisterForReflection
    public static class FinnhubEarnings {
        private String epsa;
        private String epse;
        private String report;
        private String reva;
        private String reve;
    }

    @Data
    @RegisterForReflection
    public static class Gemini
    {
        private Info info;
        private Map<String, Quarter> quarters;
        private Map<String, Target> targets;

        @Data
        @RegisterForReflection
        public static class Info
        {
            private String current_quarter_id;
            private String currency = "$";
            private String last_update;
            private String ticker;
        }

        @Data
        @RegisterForReflection
        public static class Target
        {
            private String date;
            private String institution;
            private String price;
            private String rating;
            private String source;
            private Report report;

            @Data
            @RegisterForReflection
            public static class Report
            {
                private String overview;
                private List<String> key_takeaways;
            }
        }

        @Data
        @RegisterForReflection
        public static class Quarter
        {
            private String id;
            private String ending_month;
            private String name;
            private String price_max;
            private String price_min;
            private String report_date_previous_quarter;
            private String report_date_this_quarter;
            private String reported_div;
            private String reported_eps;
            private String reported_gross_profit;
            private String reported_net_income;
            private String reported_operating_income;
            private String reported_revenues;
            private String reported_shares;
            private String reported_capex;
            private String reported_fcf;

            public boolean isInFutureOf(String quarterId)
            {
                if (quarterId == null) return true;

                PeriodName marginPeriodName = PeriodName.valueOf(quarterId);
                PeriodName thisPeriodName = PeriodName.valueOf(id);

                return thisPeriodName.compareTo(marginPeriodName) > 0;
            }

            public PeriodImportDto toImportDto()
            {
                PeriodImportDto period = new PeriodImportDto();
                period.setName(PeriodName.valueOf(this.id).toString());
                period.setEndingMonth(YearMonth.parse("20" + this.ending_month).toString());
                if (this.report_date_this_quarter != null && !this.report_date_this_quarter.isBlank()) {
                    period.setReportDate(Date.valueOf(this.report_date_this_quarter).toString());
                }
                if (this.reported_shares != null && !this.reported_shares.isBlank()) {
                    period.setShares(new BigDecimal(this.reported_shares).toString());
                }
                if (this.price_max != null && !this.price_max.isBlank()) {
                    period.setPriceHigh(new BigDecimal(this.price_max).toString());
                }
                if (this.price_min != null && !this.price_min.isBlank()) {
                    period.setPriceLow(new BigDecimal(this.price_min).toString());
                }
                if (this.reported_revenues != null && !this.reported_revenues.isBlank()) {
                    period.setRevenue(new BigDecimal(this.reported_revenues).toString());
                    period.setIsReported(true);
                }
                if (this.reported_gross_profit != null && !this.reported_gross_profit.isBlank()) {
                    period.setGrossProfit(new BigDecimal(this.reported_gross_profit).toString());
                }
                if (this.reported_operating_income != null && !this.reported_operating_income.isBlank()) {
                    period.setOperatingIncome(new BigDecimal(this.reported_operating_income).toString());
                }
                if (this.reported_net_income != null && !this.reported_net_income.isBlank()) {
                    period.setNetIncome(new BigDecimal(this.reported_net_income).toString());
                }
                if (this.reported_div != null && !this.reported_div.isBlank()) {
                    period.setDividend(new BigDecimal(this.reported_div).toString());
                }
                if (this.reported_capex != null && !this.reported_capex.isBlank()) {
                    period.setCapex(new BigDecimal(this.reported_capex).toString());
                }
                if (this.reported_fcf != null && !this.reported_fcf.isBlank()) {
                    period.setFreeCashFlow(new BigDecimal(this.reported_fcf).toString());
                }
                if (this.reported_eps != null && !this.reported_eps.isBlank()) {
                    period.setAdjustedEps(new BigDecimal(this.reported_eps).toString());
                }
                if (this.report_date_previous_quarter != null && !this.report_date_previous_quarter.isBlank()) {
                    period.setPreviousReportDate(Date.valueOf(this.report_date_previous_quarter).toString());
                }
                return period;
            }

            public PeriodImportCandidateDto toImportCandidateDto()
            {
                PeriodImportCandidateDto candidate = new PeriodImportCandidateDto();
                candidate.setName(PeriodName.valueOf(this.id).toString());
                candidate.setEndingMonth(YearMonth.parse("20" + this.ending_month).toString());
                candidate.setIsReported(this.reported_revenues != null
                        && !this.reported_revenues.isBlank());
                return candidate;
            }
        }
    }
}
