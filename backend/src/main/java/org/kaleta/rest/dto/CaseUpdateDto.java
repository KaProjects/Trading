package org.kaleta.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Everything that can change about a case. A field left out is left alone; `companies` is the
 * whole list when it is given, and `closed` opens the case again when it is false.
 */
@Data
public class CaseUpdateDto
{
    @Size(max = 100)
    private String name;

    /**
     * The companies the case covers, the whole list when it is given: one left out is one the
     * case no longer covers. A company already in the case keeps the day it joined unless a new
     * one is given; a company new to the list joins today.
     */
    @Valid
    private List<Company> companies;

    /** One company of a case: either one the app holds, by id, or a name written for one it does not. */
    @Data
    @RegisterForReflection
    public static class Company
    {
        private Long id;

        @Size(max = 50)
        private String name;

        @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
        private String addedAt;
    }

    /** The day the case was opened; a case kept from the past says when it really started. */
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String createdAt;

    /** The day the case was closed, for a case that is closed already. */
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String closedAt;

    private Boolean closed;
}
