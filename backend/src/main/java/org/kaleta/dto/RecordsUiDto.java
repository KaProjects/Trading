package org.kaleta.dto;

import lombok.Data;
import org.kaleta.entity.Company;
import org.kaleta.entity.Currency;
import org.kaleta.model.FinancialsModel;
import org.kaleta.model.RecordsModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.kaleta.Utils.format;
import static org.kaleta.Utils.formatMillions;


@Data
public class RecordsUiDto
{
    private String companyId;
    private String ticker;
    private Currency currency;
    private Boolean watching;
    private String marketCap;
    private List<RecordDto> records = new ArrayList<>();
    private Latest latestPrice;
    private Latest latestPe;
    private Latest latestPs;
    private Latest latestDy;
    private Latest latestTargets;
    private Latest latestStrategy;
    private List<Own> owns = new ArrayList<>();
    private Financials financials;

    public RecordsUiDto() {}
    public RecordsUiDto(Company company, RecordsModel recordsModel)
    {
        companyId = company.getId();
        ticker = company.getTicker();
        currency = Currency.valueOf(company.getCurrency());
        watching = company.isWatching();

        for (org.kaleta.entity.Record record : recordsModel.getSortedRecords()) {
            this.records.add(RecordDto.from(record));
        }
        setLatestPrice(Latest.from(recordsModel.getLatestPrice()));
        setLatestPe(Latest.from(recordsModel.getLatestPe()));
        setLatestPs(Latest.from(recordsModel.getLatestPs()));
        setLatestDy(Latest.from(recordsModel.getLatestDy()));
        setLatestTargets(Latest.from(recordsModel.getLatestTargets()));
        setLatestStrategy(Latest.from(recordsModel.getLatestStrategy()));

        financials = new Financials();
        financials.setHeaders(new String[]{"Quarter", "Revenue", "Net Income", "Net Margin", "EPS"});
        financials.setTtmLabels(new String[]{"revenue", "net income", "net margin", "eps", "ttm p/e", "forward p/e"});
    }

    @Data
    public static class Own
    {
        private String quantity;
        private String price;
        private String profit;
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
        private String[] headers;
        private Financial ttm;
        private String[] ttmLabels;
    }

    @Data
    public static class Financial
    {
        private String quarter;
        private String revenue;
        private String netIncome;
        private String netMargin;
        private String eps;

        private String ttmPe;
        private String forwardPe;
    }

    public void setFinancialsFrom(FinancialsModel financialsModel)
    {
        for (org.kaleta.entity.Financial financial : financialsModel.getSortedFinancials())
        {
            Financial dto = new Financial();
            dto.setQuarter(financial.getQuarter());
            dto.setRevenue(formatMillions(financial.getRevenue()));
            dto.setNetIncome(formatMillions(financial.getNetIncome()));
            dto.setNetMargin(format(financial.getNetMargin()));
            dto.setEps(format(financial.getEps()));
            financials.values.add(dto);
        }

        BigDecimal latestPrice = getLatestPrice() == null ? new BigDecimal(0) : new BigDecimal(getLatestPrice().getValue());
        FinancialsModel.Ttm ttmFinancials = financialsModel.getTtmFinancials(latestPrice);

        if (ttmFinancials != null)
        {
            financials.ttm = new Financial();
            financials.ttm.setRevenue(formatMillions(ttmFinancials.getRevenue()));
            financials.ttm.setNetIncome(formatMillions(ttmFinancials.getNetIncome()));
            financials.ttm.setNetMargin(format(ttmFinancials.getNetMargin()));
            financials.ttm.setEps(format(ttmFinancials.getEps()));
            BigDecimal ttmPe = ttmFinancials.getPe();
            financials.ttm.setTtmPe(ttmPe.compareTo(new BigDecimal(0)) > 0 ? format(ttmPe) : "-");
            BigDecimal forwardPe = financialsModel.getForwardPe(latestPrice);
            financials.ttm.setForwardPe(forwardPe.compareTo(new BigDecimal(0)) > 0 ? format(forwardPe) : "-");
        }
    }
}
