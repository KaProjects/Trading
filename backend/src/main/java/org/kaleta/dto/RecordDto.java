package org.kaleta.dto;

import lombok.Data;
import org.kaleta.entity.Record;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Data
public class RecordDto
{
    private String ticker;
    private Date date;
    private String title;
    private BigDecimal price;
    private String text;
    private BigDecimal pe;
    private BigDecimal dy;
    private String targets;
    private String strategy;

    public static List<RecordDto> from(List<Record> records)
    {
        List<RecordDto> list = new ArrayList<>();
        for (Record record : records) {
            list.add(from(record));
        }
        return list;
    }

    public static RecordDto from(Record record)
    {
        RecordDto dto = new RecordDto();
        dto.setTicker(record.getTicker().trim());
        dto.setDate(record.getDate());
        dto.setTitle(record.getTitle());
        dto.setPrice(record.getPrice());
        dto.setText(record.getText());
        dto.setPe(record.getPe());
        dto.setDy(record.getDy());
        dto.setTargets(record.getTargets());
        dto.setStrategy(record.getStrategy());
        return dto;
    }
}
