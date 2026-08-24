package org.kaleta.model;

import java.math.BigDecimal;

public record TargetStats(
        long count,
        BigDecimal minimum,
        BigDecimal average,
        BigDecimal maximum)
{
    public static TargetStats empty()
    {
        return new TargetStats(0, null, null, null);
    }
}
