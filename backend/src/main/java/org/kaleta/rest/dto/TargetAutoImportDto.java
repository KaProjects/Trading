package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.List;

/**
 * What an automatic import took in, grouped the way it is reported back to the user: one entry
 * per period, because a quarter's worth of targets arriving together is one piece of news, not
 * twenty-four.
 */
@RegisterForReflection
public record TargetAutoImportDto(List<Period> periods, List<String> warnings)
{
    public TargetAutoImportDto
    {
        periods = List.copyOf(periods);
        warnings = List.copyOf(warnings);
    }

    @RegisterForReflection
    public record Period(Long periodId, String periodName, List<Target> targets)
    {
        public Period
        {
            targets = List.copyOf(targets);
        }
    }

    @RegisterForReflection
    public record Target(String date, String institution, BigDecimal price, String rating) {}
}
