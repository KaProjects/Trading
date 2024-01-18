package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Utils;

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
}
