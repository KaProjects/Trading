package org.kaleta.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EstimateOverview
{
    private Window ttm = new Window();
    private Window current = new Window();
    private Window next1 = new Window();
    private Window next2 = new Window();
    private Window next3 = new Window();
    private BigDecimal yearOverYearChange;

    @Data
    public static class Window
    {
        private BigDecimal value;
        private BigDecimal change;
    }
}
