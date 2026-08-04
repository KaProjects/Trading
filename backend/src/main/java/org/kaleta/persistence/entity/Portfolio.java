package org.kaleta.persistence.entity;

import lombok.Getter;

public enum Portfolio
{
    PATRIA_STANDARD("Patria - Standard", "P"),
    PATRIA_MARGIN("Patria - Margin", "P-M"),
    PATRIA_DIP("Patria - DIP", "P-DIP"),
    REVOLUT_STANDARD("Revolut - Standard", "R"),
    REVOLUT_CFD("Revolut - CFD", "R-CFD"),
    ;

    @Getter
    private final String name;
    @Getter
    private final String abbreviation;

    Portfolio(String name, String abbreviation)
    {
        this.name = name;
        this.abbreviation = abbreviation;
    }
}
