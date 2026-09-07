package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record NewsSentimentLatestDto(
        NewsSentimentDto record,
        List<String> warnings)
{
    public NewsSentimentLatestDto
    {
        warnings = List.copyOf(warnings);
    }
}
