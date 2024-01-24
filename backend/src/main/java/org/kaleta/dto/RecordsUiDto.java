package org.kaleta.dto;

import lombok.Data;
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

    public static RecordsUiDto from(List<org.kaleta.entity.Record> records)
    {
        RecordsUiDto recordsUiDto = new RecordsUiDto();
        if (records.size() > 0) {
            recordsUiDto.setTicker(records.get(0).getTicker());
            recordsUiDto.setCurrency(records.get(0).getCurrency());
            recordsUiDto.setWatching(records.get(0).getWatching());
        }
        for (org.kaleta.entity.Record record : records)
        {
            recordsUiDto.getRecords().add(RecordDto.from(record));
        }
        recordsUiDto.getRecords().sort(RecordDto::compareTo);
        return recordsUiDto;
    }
}
