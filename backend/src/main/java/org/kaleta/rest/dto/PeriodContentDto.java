package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/** A day's research as the research page reads it: the text, and what the images look like. */
@RegisterForReflection
public record PeriodContentDto(Long id, String date, String content, List<Image> images)
{
    public PeriodContentDto
    {
        images = List.copyOf(images);
    }

    /** No bytes here - an image is fetched from its own endpoint when it is shown. */
    @RegisterForReflection
    public record Image(Long id, int width, int height) {}
}
