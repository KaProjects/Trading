package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Constants;
import org.kaleta.entity.Currency;

import java.util.ArrayList;
import java.util.List;

import static org.kaleta.Utils.format;

@Data
public class CompanyRecordsDto
{
    private String ticker;
    private Currency currency;
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
        }
        for (org.kaleta.entity.Record record : records)
        {
            RecordDto recordDto = new RecordDto();
            recordDto.setId(record.getId());
            recordDto.setDate(Constants.dateFormatDto.format(record.getDate()));
            recordDto.setTitle(record.getTitle());
            recordDto.setPrice(format(record.getPrice()));
            recordDto.setPe(format(record.getPe()));
            recordDto.setDy(format(record.getDy()));
            recordDto.setTargets(record.getTargets());
            recordDto.setContent(record.getContent());
            recordDto.setStrategy(record.getStrategy());
            companyRecordsDto.getRecords().add(recordDto);
        }
        companyRecordsDto.getRecords().sort(RecordDto::compareTo);
        return companyRecordsDto;
    }
}
