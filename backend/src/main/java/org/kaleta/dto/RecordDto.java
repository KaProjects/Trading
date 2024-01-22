package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Constants;
import org.kaleta.Utils;
import org.kaleta.entity.Record;

import static org.kaleta.Utils.format;

@Data
public class RecordDto implements Comparable<RecordDto>
{
    private String id;
    private String date;
    private String title;
    private String price;
    private String content;
    private String pe;
    private String dy;
    private String targets;
    private String strategy;

    @Override
    public int compareTo(RecordDto other)
    {
        return -Utils.compareDates(this.getDate(), other.getDate());
    }

    public static RecordDto from(Record record){
        RecordDto dto = new RecordDto();
        dto.setId(record.getId());
        dto.setDate(Constants.dateFormatDto.format(record.getDate()));
        dto.setTitle(record.getTitle());
        dto.setPrice(format(record.getPrice()));
        dto.setPe(format(record.getPe()));
        dto.setDy(format(record.getDy()));
        dto.setTargets(record.getTargets());
        dto.setContent(record.getContent());
        dto.setStrategy(record.getStrategy());
        return dto;
    }
}
