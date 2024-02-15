package org.kaleta.dto;

import lombok.Data;

@Data
public class DividendCreateDto
{
    private String companyId;
    private String date;
    private String dividend;
    private String tax;
}
