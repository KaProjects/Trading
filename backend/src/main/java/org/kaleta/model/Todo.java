package org.kaleta.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Todo
{
    private Long id;
    private String content;
    private LocalDateTime createdAt;
    private Long companyId;
}
