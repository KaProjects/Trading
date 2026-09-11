package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InstitutionMergeDto
{
    @NotBlank
    @Size(max = 100)
    private String sourceKey;

    @NotBlank
    @Size(max = 100)
    private String targetKey;
}
