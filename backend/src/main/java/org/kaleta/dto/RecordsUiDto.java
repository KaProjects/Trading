package org.kaleta.dto;

import lombok.Data;
import org.kaleta.entity.Company;
import org.kaleta.entity.Currency;
import org.kaleta.model.CompanyRecordsModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private List<Financial> financials = new ArrayList<>();
    private String[] financialsHeaders;
    private Financial ttmFinancial;
    private String[] ttmFinancialLabels;

    public RecordsUiDto() {}
    public RecordsUiDto(Company company, CompanyRecordsModel recordsModel)
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

        financialsHeaders = new String[]{"Quarter", "Revenue", "Net Income", "Net Margin", "EPS"};
        ttmFinancialLabels = new String[]{"revenue", "net income", "net margin", "eps", "ttm p/e", "forward p/e"};
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

        public static Latest from(CompanyRecordsModel.Latest latest){
            if (latest == null) return null;
            String value = (latest.getValue() instanceof BigDecimal)
                    ? format((BigDecimal) latest.getValue())
                    : (String) latest.getValue();
            return new Latest(value, format(latest.getDate()));
        }
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

        public int compareTo(Financial other)
        {
            int compareYears = -(Integer.parseInt(this.getQuarter().substring(0, 2)) - Integer.parseInt(other.getQuarter().substring(0, 2)));
            if (compareYears != 0) {
                return compareYears;
            } else {
                return -(Integer.parseInt(this.getQuarter().substring(3, 4)) - Integer.parseInt(other.getQuarter().substring(3, 4)));
            }
        }
    }

    public void setFinancialsFrom(List<org.kaleta.entity.Financial> financials)
    {
        for (org.kaleta.entity.Financial financial : financials)
        {
            Financial dto = new Financial();
            dto.setQuarter(financial.getQuarter());
            dto.setRevenue(formatMillions(financial.getRevenue()));
            dto.setNetIncome(formatMillions(financial.getNetIncome()));
            dto.setNetMargin(format(financial.getNetMargin()));
            dto.setEps(format(financial.getEps()));
            getFinancials().add(dto);
        }
        getFinancials().sort(Financial::compareTo);

        if (financials.size() > 0)
        {
            BigDecimal revenue = new BigDecimal(0);
            BigDecimal netIncome = new BigDecimal(0);
            BigDecimal eps = new BigDecimal(0);
            financials.sort(org.kaleta.entity.Financial::compareTo);
            for (int i=0; i<4; i++){
                if (financials.size() > i){
                    revenue = revenue.add(financials.get(i).getRevenue());
                    netIncome = netIncome.add(financials.get(i).getNetIncome());
                    eps = eps.add(financials.get(i).getEps());
                } else {
                    BigDecimal multiplier = new BigDecimal(4).divide(new BigDecimal(i), 4, RoundingMode.HALF_UP);
                    revenue = revenue.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                    netIncome = netIncome.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                    eps = eps.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                    break;
                }
            }
            ttmFinancial = new Financial();
            ttmFinancial.setRevenue(formatMillions(revenue));
            ttmFinancial.setNetIncome(formatMillions(netIncome));
            ttmFinancial.setNetMargin(format(netIncome.divide(revenue, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100))));
            ttmFinancial.setEps(format(eps));
            if (getLatestPrice() != null){
                BigDecimal latestPrice = new BigDecimal(getLatestPrice().getValue());
                ttmFinancial.setTtmPe(eps.compareTo(new BigDecimal(0)) > 0 ? format(latestPrice.divide(eps,2, RoundingMode.HALF_UP)) : "-");
                ttmFinancial.setForwardPe(financials.get(0).getEps().compareTo(new BigDecimal(0)) > 0 ? format(latestPrice.divide(financials.get(0).getEps().multiply(new BigDecimal(4)), 2, RoundingMode.HALF_UP)) : "-");
            }
        }
    }
}
