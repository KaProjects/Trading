package org.kaleta.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record OnboardingTargetsDto(
        String ticker,
        String from,
        String to,
        List<String> institutions,
        List<Target> targets,
        Stats stats,
        Report report,
        List<String> warnings)
{
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

    public record Stats(
            int count,
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal average)
    {
    }

    public record Report(
            String overview,
            List<String> keyTakeaways)
    {
    }
}
