package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "^[A-Z0-9.-]{1,30}$", message = "must be a valid Alpha Vantage ticker")
    private String alphaVantageTicker;
    @NotNull
    @ValueOfEnum(enumClass = Currency.class)
    private String currency;
    @ValueOfEnum(enumClass = Sector.class)
    private String sector;
}
