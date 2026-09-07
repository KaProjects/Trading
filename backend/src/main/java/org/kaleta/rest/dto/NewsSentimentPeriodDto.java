package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;
import java.util.List;

@RegisterForReflection
public record NewsSentimentPeriodDto(
        List<NewsSentimentDto> records,
        Window window,
        List<String> warnings)
{
    public NewsSentimentPeriodDto
    {
        records = List.copyOf(records);
        warnings = List.copyOf(warnings);
    }

    @RegisterForReflection
    public record Window(LocalDate start, LocalDate end) {}
}
