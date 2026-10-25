package org.kaleta.rest.dto;

import lombok.Data;

@Data
public class DisqualifiedCompanyDto
{
    private String ticker;
    private String reason;
    /** The disqualification is only a stale or missing cached price, so re-syncing it may clear it. */
    private boolean priceSyncable;
}
