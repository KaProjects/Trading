package org.kaleta.framework;

import java.util.Arrays;
import java.util.List;

public class InvalidValues
{
    public static List<String> invalidDates()
    {
        return Arrays.asList("", "x", "2020-30-1", "2030-12-40", "1.1.2012");
    }

    public static List<String> invalidBigDecimals()
    {
        return Arrays.asList("", "x", "10_000", "10$", "1,000.0", "+-1", "1.0.0");
    }
}
