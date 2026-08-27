package org.kaleta.rest.dto;

import java.time.LocalDate;
import java.util.List;

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

    public record Window(LocalDate start, LocalDate end) {}
}
