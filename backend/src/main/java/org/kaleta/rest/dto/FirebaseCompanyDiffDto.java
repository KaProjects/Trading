package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record FirebaseCompanyDiffDto(List<String> onlyInFirebase, List<String> warnings)
{
    public FirebaseCompanyDiffDto
    {
        onlyInFirebase = List.copyOf(onlyInFirebase);
        warnings = List.copyOf(warnings);
    }
}
