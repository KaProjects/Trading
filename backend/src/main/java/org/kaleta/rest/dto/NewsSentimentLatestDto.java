package org.kaleta.rest.dto;

import java.util.List;

public record NewsSentimentLatestDto(
        NewsSentimentDto record,
        List<String> warnings)
{
    public NewsSentimentLatestDto
    {
        warnings = List.copyOf(warnings);
    }
}
