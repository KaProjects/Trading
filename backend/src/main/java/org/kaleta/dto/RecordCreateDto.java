package org.kaleta.dto;

import lombok.Data;

@Deprecated
@Data
public class RecordCreateDto
{
    private String date;
    private String title;
    private String price;
    private String companyId;
}
