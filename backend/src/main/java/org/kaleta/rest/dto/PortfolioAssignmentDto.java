package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.rest.validation.ValueOfEnum;

import java.util.ArrayList;
import java.util.List;

@Data
public class PortfolioAssignmentDto
{
    @NotNull
    @Size(min = 1)
    private List<@NotNull @ValidId Long> tradeIds = new ArrayList<>();

    @NotNull
    @ValueOfEnum(enumClass = Portfolio.class)
    private String portfolio;
}
