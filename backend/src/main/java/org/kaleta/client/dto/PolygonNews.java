package org.kaleta.client.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record PolygonNews(
        String id,
        String title,
        String publisher,
        String publishedUtc,
        String articleUrl,
        String description,
        List<Insight> insights)
{
    @RegisterForReflection
    public record Insight(
            String ticker,
            String sentiment,
            String sentimentReasoning)
    {
    }
}
