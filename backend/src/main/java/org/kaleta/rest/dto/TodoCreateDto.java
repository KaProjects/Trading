package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TodoCreateDto
{
    @NotBlank
    private String content;
}
