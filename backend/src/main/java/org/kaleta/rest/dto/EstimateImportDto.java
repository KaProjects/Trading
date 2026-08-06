package org.kaleta.rest.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EstimateImportDto
{
    private Quarter current;
    private Quarter next1;
    private Quarter next2;
    private Quarter next3;
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class Quarter
    {
        private String eps;
        private String date;
    }
}
