package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RegisterForReflection
public record NewsSentimentDto(
        String id,
        LocalDate date,
        int total,
        Map<String, Integer> stats,
        List<String> keyTakeaways)
{
    public NewsSentimentDto
    {
        stats = Map.copyOf(stats);
        keyTakeaways = List.copyOf(keyTakeaways);
    }
}
