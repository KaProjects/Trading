package org.kaleta.rest.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** A table as it is written: the whole grid every time, because that is how it is edited. */
@Data
public class CaseTableUpdateDto
{
    private String title;
    private List<String> columns = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Row
    {
        private String label;
        private List<Cell> cells = new ArrayList<>();
    }

    @Data
    public static class Cell
    {
        private String value;
        private String rating;
    }
}
