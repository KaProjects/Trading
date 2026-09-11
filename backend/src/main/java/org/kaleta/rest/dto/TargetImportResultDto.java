package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record TargetImportResultDto(int imported, int discarded, List<String> warnings)
{
    public TargetImportResultDto
    {
        warnings = List.copyOf(warnings);
    }
}
