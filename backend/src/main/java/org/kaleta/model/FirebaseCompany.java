package org.kaleta.model;

import lombok.Data;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.PeriodImportDto;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.Map;

@Data
public class FirebaseCompany
{
    private Map<String, Map<String, FinnhubEarnings>> fhe;
    private Gemini gemini;

    @Data
    public static class FinnhubEarnings {
        private Double epsa;
        private Double epse;
        private String report;
        private Double reva;
        private Double reve;
    }

    @Data
    public static class Gemini
    {
        private Info info;
        private Map<String, Quarter> quarters;

        @Data
        public static class Info
        {
            private String current_quarter_id;
            private String last_update;
            private String ticker;
        }

        @Data
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

            public boolean isInFutureOf(String quarterId)
            {
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
                if (this.report_date_previous_quarter != null && !this.report_date_previous_quarter.isBlank()) {
                    period.setPreviousReportDate(Date.valueOf(this.report_date_previous_quarter).toString());
                }
                return period;
            }
        }
    }
}
