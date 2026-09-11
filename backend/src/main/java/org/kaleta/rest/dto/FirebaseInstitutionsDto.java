package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record FirebaseInstitutionsDto(List<Institution> institutions, List<String> warnings)
{
    public FirebaseInstitutionsDto
    {
        institutions = List.copyOf(institutions);
        warnings = List.copyOf(warnings);
    }

    @RegisterForReflection
    public record Institution(
            String key,
            String name,
            boolean enabled,
            boolean trusted,
            List<String> aliases) {}
}
