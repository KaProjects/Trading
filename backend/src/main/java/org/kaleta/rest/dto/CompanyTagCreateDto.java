package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.kaleta.rest.validation.ValidId;

@Data
public class CompanyTagCreateDto
{
    @NotNull
    @ValidId
    private Long companyId;

    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "\\S+", message = "must not contain whitespace")
    private String value;
}
