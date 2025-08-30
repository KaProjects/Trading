package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Constants;
import org.kaleta.Utils;
import org.kaleta.entity.Record;

import static org.kaleta.Utils.format;

@Deprecated
@Data
public class RecordDto
{
    private String id;
    private String date;
    private String title;
    private String price;
    private String content;
    private String pe;
    private String ps;
    private String dy;
    private String targets;
    private String strategy;

    public static RecordDto from(Record record){
        RecordDto dto = new RecordDto();
        dto.setId(record.getId());
        dto.setDate(Utils.format(record.getDate()));
        dto.setTitle(record.getTitle());
        dto.setPrice(format(record.getPrice()));
        dto.setPe(format(record.getPe()));
        dto.setPs(format(record.getPs()));
        dto.setDy(format(record.getDy()));
        dto.setTargets(record.getTargets());
        dto.setContent(record.getContent());
        dto.setStrategy(record.getStrategy());
        return dto;
    }
}
