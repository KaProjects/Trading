package org.kaleta.dto;

import lombok.Data;
import org.kaleta.model.FinancialsModel;
import org.kaleta.model.RecordsModel;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Record;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.kaleta.Utils.format;
import static org.kaleta.Utils.formatMillions;
import static org.kaleta.Utils.formatNoDecimal;

@Deprecated
@Data
public class RecordsUiDto
{
    private CompanyDto company;
    private String marketCap;
    private List<RecordDto> records = new ArrayList<>();
    private Latests latest;
    private List<Own> owns = new ArrayList<>();
    private Financials financials;

    public RecordsUiDto() {}
    public RecordsUiDto(Company company, RecordsModel recordsModel)
    {
        this.company = CompanyDto.from(company);

        for (Record record : recordsModel.getSortedRecords()) {
            this.records.add(RecordDto.from(record));
        }
        latest = new Latests();
        latest.price = Latest.from(recordsModel.getLatestPrice());
        latest.pe = Latest.from(recordsModel.getLatestPe());
        latest.ps = Latest.from(recordsModel.getLatestPs());
        latest.dy = Latest.from(recordsModel.getLatestDy());
        latest.targets = Latest.from(recordsModel.getLatestTargets());
        latest.strategy = Latest.from(recordsModel.getLatestStrategy());

        financials = new Financials();
    }

    @Data
    public static class Own
    {
        private String quantity;
        private String price;
        private String profit;
    }

    @Data
    public static class Latests
    {
        private Latest price;
        private Latest pe;
        private Latest ps;
        private Latest dy;
        private Latest targets;
        private Latest strategy;
    }

    @Data
    public static class Latest
    {
        private String value;
        private String date;

        public Latest(String value, String date) {this.value = value; this.date = date;}

        public static Latest from(RecordsModel.Latest latest){
            if (latest == null) return null;
            String value = (latest.getValue() instanceof BigDecimal)
                    ? format((BigDecimal) latest.getValue())
                    : (String) latest.getValue();
            return new Latest(value, format(latest.getDate()));
        }
    }

    @Data
    public static class Financials
    {
        private List<Financial> values = new ArrayList<>();
        private Financial ttm;
    }

    @Data
    public static class Financial
    {
        private String quarter;
        private String revenue;
        private String costGoodsSold;
        private String grossProfit;
        private String grossMargin;
        private String operatingExpenses;
        private String operatingIncome;
        private String operatingMargin;
        private String netIncome;
        private String netMargin;
    }

    public void setLatestPrice(Latest latestPrice)
    {
        latest.price = latestPrice;
    }

    public void setFinancialsFrom(FinancialsModel financialsModel)
    {
        for (org.kaleta.persistence.entity.Financial financial : financialsModel.getSortedFinancials())
        {
            Financial dto = new Financial();
            dto.setQuarter(financial.getQuarter());
            dto.setRevenue(formatMillions(financial.getRevenue()));
            dto.setCostGoodsSold(formatMillions(financial.getCostGoodsSold()));
            dto.setGrossProfit(formatMillions(financial.getGrossProfit()));
            dto.setGrossMargin(formatNoDecimal(financial.getGrossMargin()));
            dto.setOperatingExpenses(formatMillions(financial.getOperatingExpenses()));
            dto.setOperatingIncome(formatMillions(financial.getOperatingIncome()));
            dto.setOperatingMargin(formatNoDecimal(financial.getOperatingMargin()));
            dto.setNetIncome(formatMillions(financial.getNetIncome()));
            dto.setNetMargin(formatNoDecimal(financial.getNetMargin()));
            financials.values.add(dto);
        }

        org.kaleta.persistence.entity.Financial ttmFinancials = financialsModel.getTtmFinancials();

        if (ttmFinancials != null)
        {
            financials.ttm = new Financial();
            financials.ttm.setRevenue(formatMillions(ttmFinancials.getRevenue()));
            financials.ttm.setCostGoodsSold(formatMillions(ttmFinancials.getRevenue()));
            financials.ttm.setGrossProfit(formatMillions(ttmFinancials.getGrossProfit()));
            financials.ttm.setGrossMargin(formatNoDecimal(ttmFinancials.getGrossMargin()));
            financials.ttm.setOperatingExpenses(formatMillions(ttmFinancials.getOperatingExpenses()));
            financials.ttm.setOperatingIncome(formatMillions(ttmFinancials.getOperatingIncome()));
            financials.ttm.setOperatingMargin(formatNoDecimal(ttmFinancials.getOperatingMargin()));
            financials.ttm.setNetIncome(formatMillions(ttmFinancials.getNetIncome()));
            financials.ttm.setNetMargin(formatNoDecimal(ttmFinancials.getNetMargin()));
        }
    }
}
