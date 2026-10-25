package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record SplitResultDto(int updatedTrades, boolean recordCreated, List<String> warnings)
{
    public SplitResultDto
    {
        warnings = List.copyOf(warnings);
    }
}
