package org.kaleta.model;

import lombok.Data;
import org.kaleta.persistence.entity.CompanyWithStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Data
public class CompanyGroups
{
    private final List<CompanyWithStats> watching = new ArrayList<>();
    private final List<CompanyWithStats> deprecated = new ArrayList<>();
    private final List<CompanyWithStats> owned = new ArrayList<>();
    private final List<CompanyWithStats> unreported = new ArrayList<>();
    private final Map<String, List<CompanyWithStats>> sectors = new TreeMap<>();
}
