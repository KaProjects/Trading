package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.persistence.entity.Record;

import static org.kaleta.Utils.format;

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

    @Deprecated
    public static RecordDto from(Record record){
        RecordDto dto = new RecordDto();
        dto.setId(record.getId());
        dto.setDate(Utils.format(record.getDate()));
        dto.setTitle(record.getTitle());
        dto.setPrice(format(record.getPrice()));
        dto.setPe(format(record.getPriceToNetIncome()));
        dto.setPs(format(record.getPriceToRevenues()));
        dto.setDy(format(record.getDividendYield()));
        dto.setTargets(record.getTargets());
        dto.setContent(record.getContent());
        dto.setStrategy(record.getStrategy());
        return dto;
    }
}
