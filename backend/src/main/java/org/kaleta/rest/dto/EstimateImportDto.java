package org.kaleta.rest.dto;

import lombok.Data;

@Data
public class EstimateImportDto
{
    private Quarter past4;
    private Quarter past3;
    private Quarter past2;
    private Quarter past1;
    private Quarter current;
    private Quarter next1;
    private Quarter next2;
    private Quarter next3;

    @Data
    public static class Quarter
    {
        private String eps;
        private String date;
    }
}
