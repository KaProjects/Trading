package org.kaleta.rest.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OutperformersPriceSyncDto
{
    private int updated;
    private List<String> warnings = new ArrayList<>();
    private OutperformersDto outperformers;
}
