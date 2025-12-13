package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @NotNull
    @Pattern(regexp = "^true|false$", message = "must be 'true' or 'false'")
    private String watching;
    @NotNull
    @ValueOfEnum(enumClass = Sector.class)
    private String sector;
}
