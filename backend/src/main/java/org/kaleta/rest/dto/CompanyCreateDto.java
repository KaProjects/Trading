package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.validation.ValidTicker;
import org.kaleta.rest.validation.ValueOfEnum;

@Data
public class CompanyCreateDto
{
    @NotNull
    @ValidTicker
    private String ticker;
    @NotNull
    @ValueOfEnum(enumClass = Currency.class)
    private String currency;
    @ValueOfEnum(enumClass = Sector.class)
    private String sector;
}
