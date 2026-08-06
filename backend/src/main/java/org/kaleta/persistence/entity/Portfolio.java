package org.kaleta.persistence.entity;

import lombok.Getter;

public enum Portfolio
{
    FIDELITY_ORCL("Fidelity - ORCL", "Fo"),
    PATRIA_STANDARD("Patria - Standard", "P"),
    PATRIA_MARGIN("Patria - Margin", "Pm"),
    PATRIA_DIP("Patria - DIP", "Pd"),
    REVOLUT_STANDARD("Revolut - Standard", "R"),
    REVOLUT_CFD("Revolut - CFD", "Rd"),
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
