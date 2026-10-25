package org.kaleta.rest.dto;

import lombok.Data;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/** A case as the app reads it: its own fields, plus the companies it covers by ticker. */
@Data
public class CaseDto
{
    private Long id;
    private String name;
    private Date createdAt;
    private Date closedAt;
    private List<Company> companies = new ArrayList<>();
    /** The days written into the case, oldest first. Only filled in when one case is read. */
    private List<CaseEntryDto> entries = new ArrayList<>();

    @Data
    public static class Company
    {
        private Long id;
        private String ticker;
    }
}
