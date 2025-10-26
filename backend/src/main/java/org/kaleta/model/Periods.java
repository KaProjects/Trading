package org.kaleta.model;

import lombok.Data;
import org.kaleta.persistence.entity.Period;

import java.util.ArrayList;
import java.util.List;

@Data
public class Periods
{
    private List<Period> periods = new ArrayList<>();
    private List<Financial> financials = new ArrayList<>();
    private Financial ttm;
}
