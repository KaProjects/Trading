package org.kaleta.rest.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TargetDto
{
    private Long id;
    private Long periodId;
    private String date;
    private String institution;
    private BigDecimal price;
    private String rating;
    private String overview;
    private String takeaway1;
    private String takeaway2;
    private String takeaway3;
    private String takeaway4;
}
