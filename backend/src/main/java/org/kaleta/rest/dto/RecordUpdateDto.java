package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.kaleta.rest.validation.ValidUuid;

@Data
public class RecordUpdateDto
{
    @NotNull
    @ValidUuid
    private String id;
    private String title;
    private String content;
    private String strategy;
}
