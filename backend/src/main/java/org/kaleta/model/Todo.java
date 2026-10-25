package org.kaleta.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@RegisterForReflection
public class Todo
{
    private Long id;
    private String content;
    private LocalDateTime createdAt;
    private Long companyId;
}
