package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterForReflection
public record TargetSyncCountsDto(
        Map<Long, Integer> counts,
        Set<Long> failedPeriodIds,
        List<String> warnings)
{
    public TargetSyncCountsDto
    {
        counts = Map.copyOf(counts);
        failedPeriodIds = Set.copyOf(failedPeriodIds);
        warnings = List.copyOf(warnings);
    }
}
