package org.kaleta.client.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.List;

@RegisterForReflection
public record GeminiTargets(
        List<Target> targets,
        Report report)
{
    @RegisterForReflection
    public record Target(
            String institution,
            String date,
            BigDecimal price,
            String rating,
            String source,
            String overview,
            List<String> keyTakeaways)
    {
    }

    @RegisterForReflection
    public record Report(
            String overview,
            List<String> keyTakeaways)
    {
    }
}
