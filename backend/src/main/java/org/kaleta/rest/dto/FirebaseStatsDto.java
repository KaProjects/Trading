package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record FirebaseStatsDto(List<CompanyStats> companies, List<String> warnings)
{
    public FirebaseStatsDto
    {
        companies = List.copyOf(companies);
        warnings = List.copyOf(warnings);
    }

    @RegisterForReflection
    public record CompanyStats(
            String ticker,
            boolean inDatabase,
            int geminiQuarters,
            int geminiTargets,
            int finnhubEarnings,
            int newsSentiments) {}
}
