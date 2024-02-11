package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.entity.Company;
import org.kaleta.entity.Currency;

import java.util.ArrayList;
import java.util.List;

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

    public void setCompany(Company company){
        companyId = company.getId();
        ticker = company.getTicker();
        currency = Currency.valueOf(company.getCurrency());
        watching = company.isWatching();
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
