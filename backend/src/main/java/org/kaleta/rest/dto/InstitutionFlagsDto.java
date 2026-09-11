package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InstitutionFlagsDto
{
    @NotNull
    private Boolean enabled;

    @NotNull
    private Boolean trusted;
}
