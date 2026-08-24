package org.kaleta.rest.dto;

import java.util.List;

public record TargetSyncDto(int count, List<String> warnings)
{
    public TargetSyncDto
    {
        warnings = List.copyOf(warnings);
    }
}
