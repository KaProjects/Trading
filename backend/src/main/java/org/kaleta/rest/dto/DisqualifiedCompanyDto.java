package org.kaleta.rest.dto;

import lombok.Data;

@Data
public class DisqualifiedCompanyDto
{
    private String ticker;
    private String reason;
}
