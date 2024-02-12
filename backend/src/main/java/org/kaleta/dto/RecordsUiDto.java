package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.entity.Company;
import org.kaleta.entity.Currency;

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
    public RecordsUiDto(List<org.kaleta.entity.Record> records)
    {
        records.sort((recordA, recordB) -> -Utils.compareDbDates(recordA.getDate(), recordB.getDate()));

        if (records.size() > 0) {
            this.setCompany(records.get(0).getCompany());
        }
        for (org.kaleta.entity.Record record : records)
        {
            this.getRecords().add(RecordDto.from(record));
        }
        computeLatest();
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

    public void setCompany(Company company){
        companyId = company.getId();
        ticker = company.getTicker();
        currency = Currency.valueOf(company.getCurrency());
        watching = company.isWatching();
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

    private void computeLatest(){
        if (this.getRecords().size() > 0)
        {
            for(int i=0; i < this.getRecords().size(); i++)
            {
                RecordDto iRecord = this.getRecords().get(i);

                if (getLatestPrice() == null && iRecord.getPrice() != null && !iRecord.getPrice().isBlank()){
                    setLatestPrice(new Latest(iRecord.getPrice(), iRecord.getDate()));
                }
                if (getLatestPe() == null && iRecord.getPe() != null && !iRecord.getPe().isBlank()){
                    setLatestPe(new Latest(iRecord.getPe(), iRecord.getDate()));
                }
                if (getLatestPs() == null && iRecord.getPs() != null && !iRecord.getPs().isBlank()){
                    setLatestPs(new Latest(iRecord.getPs(), iRecord.getDate()));
                }
                if (getLatestDy() == null && iRecord.getDy() != null && !iRecord.getDy().isBlank()){
                    setLatestDy(new Latest(iRecord.getDy(), iRecord.getDate()));
                }
                if (getLatestTargets() == null && iRecord.getTargets() != null && !iRecord.getTargets().isBlank()){
                    setLatestTargets(new Latest(iRecord.getTargets(), iRecord.getDate()));
                }
                if (getLatestStrategy() == null && iRecord.getStrategy() != null && !iRecord.getStrategy().isBlank()){
                    setLatestStrategy(new Latest(iRecord.getStrategy(), iRecord.getDate()));
                }
            }
        }
    }
}
