package org.kaleta.rest.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
