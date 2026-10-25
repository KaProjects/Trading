package org.kaleta.rest.dto;

import java.util.List;

public record OnboardingNewsDto(
        String ticker,
        String from,
        Sentiment sentiment,
        List<Article> articles,
        List<String> warnings)
{
    public record Sentiment(
            int total,
            int positive,
            int neutral,
            int negative,
            int unrated)
    {
    }

    public record Article(
            String title,
            String publisher,
            String published,
            String url,
            String sentiment,
            String reasoning)
    {
    }
}
