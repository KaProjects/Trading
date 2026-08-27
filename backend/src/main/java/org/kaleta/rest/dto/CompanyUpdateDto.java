package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Exchange;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.rest.validation.ValueOfEnum;

@Data
public class CompanyUpdateDto
{
    @NotNull
    @ValidId
    private Long id;

    @Pattern(regexp = "^[A-Z0-9.-]{1,30}$", message = "must be a valid Alpha Vantage ticker")
    private String alphaVantageTicker;

    @ValueOfEnum(enumClass = Exchange.class)
    private String exchange;

    @Size(max = 150)
    private String name;

    @Size(max = 5000)
    private String description;

    @Size(max = 500)
    @Pattern(regexp = "^https?://\\S+$", message = "must be an HTTP(S) URL")
    private String logoUrl;

    @Size(max = 500)
    @Pattern(regexp = "^https?://\\S+$", message = "must be an HTTP(S) URL")
    private String website;

    @NotNull
    @ValueOfEnum(enumClass = Currency.class)
    private String currency;

    @ValueOfEnum(enumClass = Sector.class)
    private String sector;
}
