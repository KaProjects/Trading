package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Constants;
import org.kaleta.Utils;
import org.kaleta.entity.Currency;

import java.util.ArrayList;
import java.util.List;

import static org.kaleta.Utils.format;

@Data
public class RecordDto
{
    private String ticker;
    private Currency currency;
    private List<Record> records = new ArrayList<>();
    private String lastPrice;
    private String lastStrategy;
    private List<Own> owns = new ArrayList<>();

    @Data
    public static class Record implements Comparable<Record>
    {
        private String date;
        private String title;
        private String price;
        private String content;
        private String pe;
        private String dy;
        private String targets;
        private String strategy;

        @Override
        public int compareTo(RecordDto.Record other)
        {
            return -Utils.compareDates(this.getDate(), other.getDate());
        }
    }

    @Data
    public static class Own
    {
        private String quantity;
        private String price;
        private String profit;
    }

    public static RecordDto from(List<org.kaleta.entity.Record> records)
    {
        RecordDto recordDto = new RecordDto();
        if (records.size() > 0) {
            recordDto.setTicker(records.get(0).getTicker());
            recordDto.setCurrency(records.get(0).getCurrency());
        }
        for (org.kaleta.entity.Record record : records)
        {
            RecordDto.Record dto = new RecordDto.Record();
            dto.setDate(Constants.dateFormat.format(record.getDate()));
            dto.setTitle(record.getTitle());
            dto.setPrice(format(record.getPrice()));
            dto.setPe(format(record.getPe()));
            dto.setDy(format(record.getDy()));
            dto.setTargets(record.getTargets());
            dto.setContent(record.getContent());
            dto.setStrategy(record.getStrategy());
            recordDto.getRecords().add(dto);
        }
        recordDto.getRecords().sort(RecordDto.Record::compareTo);
        return recordDto;
    }
}
