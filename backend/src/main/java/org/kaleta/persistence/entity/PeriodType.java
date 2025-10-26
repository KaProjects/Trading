package org.kaleta.persistence.entity;

import lombok.Getter;

public enum PeriodType
{
    FY(1), H1(1), H2(2), Q1(1), Q2(2), Q3(3), Q4(4);

    @Getter
    private final int number;

    PeriodType(int number)
    {
        this.number = number;
    }
}
