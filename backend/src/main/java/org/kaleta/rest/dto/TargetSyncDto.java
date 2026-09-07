package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record TargetSyncDto(int count, List<String> warnings)
{
    public TargetSyncDto
    {
        warnings = List.copyOf(warnings);
    }
}
