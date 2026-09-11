package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.List;

@RegisterForReflection
public record TargetCandidatesDto(List<Candidate> candidates, List<String> warnings)
{
    public TargetCandidatesDto
    {
        candidates = List.copyOf(candidates);
        warnings = List.copyOf(warnings);
    }

    @RegisterForReflection
    public record Candidate(
            String date,
            String institution,
            BigDecimal price,
            String rating,
            String overview,
            String takeaway1,
            String takeaway2,
            String takeaway3,
            String takeaway4) {}
}
