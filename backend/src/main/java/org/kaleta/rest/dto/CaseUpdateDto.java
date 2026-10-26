package org.kaleta.rest.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Everything that can change about a case. A field left out is left alone; `companyIds` is the
 * whole list when it is given, and `closed` opens the case again when it is false.
 */
@Data
public class CaseUpdateDto
{
    @Size(max = 100)
    private String name;

    private List<Long> companyIds;

    /** Companies the app does not hold; the whole list when it is given. */
    private List<String> companyNames;

    /** The day the case was opened; a case kept from the past says when it really started. */
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String createdAt;

    /** The day the case was closed, for a case that is closed already. */
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String closedAt;

    private Boolean closed;
}
