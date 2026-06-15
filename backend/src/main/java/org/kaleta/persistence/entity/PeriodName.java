package org.kaleta.persistence.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Year;

@Data
@EqualsAndHashCode
public final class PeriodName implements Comparable<PeriodName>
{
    private Year year;
    private PeriodType type;

    @Override
    public String toString()
    {
        return year.toString().substring(2, 4) + type;
    }

    public static PeriodName valueOf(String value)
    {
        if (value == null || value.isBlank() || value.length() != 4)
        {
            throw new IllegalArgumentException("Invalid period name '" + value + "'");
        }
        PeriodName periodName = new PeriodName();
        periodName.setYear(Year.parse("20" + value.substring(0, 2)));
        periodName.setType(PeriodType.valueOf(value.substring(2, 4)));
        return periodName;
    }

    @Override
    public int compareTo(PeriodName period)
    {
        int comparedYears = year.compareTo(period.getYear());
        if (comparedYears != 0) return comparedYears;
        return type.compareTo(period.getType());
    }
}
