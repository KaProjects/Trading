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
        List<String> keyTakeaways,
        List<Point> bull,
        List<Point> bear,
        boolean bullBearAnalysed)
{
    public NewsSentimentDto
    {
        stats = Map.copyOf(stats);
        keyTakeaways = List.copyOf(keyTakeaways);
        bull = List.copyOf(bull);
        bear = List.copyOf(bear);
    }

    public NewsSentimentDto withCases(List<Point> bull, List<Point> bear)
    {
        return new NewsSentimentDto(id, date, total, stats, keyTakeaways, bull, bear, true);
    }

    @RegisterForReflection
    public record Point(String point, String reasoning) {}
}
