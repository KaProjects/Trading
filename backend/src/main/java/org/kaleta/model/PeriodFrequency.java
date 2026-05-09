package org.kaleta.model;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum PeriodFrequency
{
    YEARLY, QUARTERLY, MONTHLY;

    public String parseDate(String date)
    {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw new IllegalArgumentException("invalid date format");
        switch (this) {
            case YEARLY:
                return date.substring(0, 4);
            case MONTHLY:
                return date.substring(0, 7);
            case QUARTERLY:
                switch(date.substring(5, 7)) {
                    case "01": case "02": case "03":
                        return date.substring(0, 5) + "Q1";
                    case "04": case "05": case "06":
                        return date.substring(0, 5) + "Q2";
                    case  "07": case "08": case "09":
                        return date.substring(0, 5) + "Q3";
                    case  "10": case "11": case "12":
                        return date.substring(0, 5) + "Q4";
                    default:
                        throw new IllegalArgumentException("unexpected month value: " + date.substring(5, 7));
                }
            default:
                throw new IllegalArgumentException("unexpected frequency " + this);
        }
    }

    public List<String> getAllYearKeys(String date)
    {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw new IllegalArgumentException("invalid date format");
        switch (this) {
            case YEARLY:
                return List.of(date.substring(0, 4));
            case  MONTHLY:
                return Stream.of("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12")
                        .map(month -> date.substring(0, 5) + month)
                        .collect(Collectors.toList());
            case  QUARTERLY:
                return Stream.of("Q1", "Q2", "Q3", "Q4")
                        .map(quarter -> date.substring(0, 5) + quarter)
                        .collect(Collectors.toList());
            default:
                throw new IllegalArgumentException("unexpected frequency " + this);
        }
    }
}
