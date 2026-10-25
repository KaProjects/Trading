package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * A table of a case entry: the companies across the top, one metric per row, and a value with a
 * rating ("++", "+", "~", "-", "--", or nothing) in each cell.
 */
@RegisterForReflection
public record CaseTableDto(Long id, int position, String title, List<String> columns, List<Row> rows)
{
    @RegisterForReflection
    public record Row(String label, List<Cell> cells) {}

    @RegisterForReflection
    public record Cell(String value, String rating) {}
}
