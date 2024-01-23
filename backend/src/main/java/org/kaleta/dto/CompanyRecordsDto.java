package org.kaleta.dto;

import lombok.Data;
import org.kaleta.entity.Currency;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompanyRecordsDto
{
    private String companyId;
    private String ticker;
    private Currency currency;
    private Boolean watching;
    private List<RecordDto> records = new ArrayList<>();
    private String lastPrice;
    private String lastStrategy;
    private List<Own> owns = new ArrayList<>();

    @Data
    public static class Own
    {
        private String quantity;
        private String price;
        private String profit;
    }

    public static CompanyRecordsDto from(List<org.kaleta.entity.Record> records)
    {
        CompanyRecordsDto companyRecordsDto = new CompanyRecordsDto();
        if (records.size() > 0) {
            companyRecordsDto.setTicker(records.get(0).getTicker());
            companyRecordsDto.setCurrency(records.get(0).getCurrency());
            companyRecordsDto.setWatching(records.get(0).getWatching());
        }
        for (org.kaleta.entity.Record record : records)
        {
            companyRecordsDto.getRecords().add(RecordDto.from(record));
        }
        companyRecordsDto.getRecords().sort(RecordDto::compareTo);
        return companyRecordsDto;
    }
}
