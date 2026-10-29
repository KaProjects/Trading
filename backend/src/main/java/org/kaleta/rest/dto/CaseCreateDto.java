package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CaseCreateDto
{
    @NotBlank
    @Size(max = 100)
    private String name;

    private List<Long> companyIds = new ArrayList<>();

    /** Companies the app does not hold, as the names they were written as. */
    private List<String> companyNames = new ArrayList<>();
}
