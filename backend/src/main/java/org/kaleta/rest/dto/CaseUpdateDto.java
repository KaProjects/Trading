package org.kaleta.rest.dto;

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

    private Boolean closed;
}
