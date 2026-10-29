package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A day written into a case. The date is today for a new day, or the date of a day the case
 * already has; a case is written forwards, so nothing is back-dated into it.
 */
@Data
public class CaseEntryUpdateDto
{
    @NotBlank
    private String date;

    private String content;
}
