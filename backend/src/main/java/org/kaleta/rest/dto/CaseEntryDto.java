package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/** A day of a case as the page reads it: the text, and what the pictures look like. */
@RegisterForReflection
public record CaseEntryDto(Long id, String date, String content, List<Image> images,
                          List<CaseTableDto> tables)
{
    public CaseEntryDto
    {
        images = List.copyOf(images);
        tables = List.copyOf(tables);
    }

    @RegisterForReflection
    public record Image(Long id, int width, int height) {}
}
