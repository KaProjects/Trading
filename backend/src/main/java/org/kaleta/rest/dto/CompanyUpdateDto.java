package org.kaleta.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.validation.ValidUuid;
import org.kaleta.rest.validation.ValueOfEnum;

@Data
public class CompanyUpdateDto
{
    @NotNull
    @ValidUuid
    private String id;
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
