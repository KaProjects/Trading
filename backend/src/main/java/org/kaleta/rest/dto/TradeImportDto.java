package org.kaleta.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValueOfEnum;

import java.util.ArrayList;
import java.util.List;

@Data
public class TradeImportDto
{
    @NotNull
    @Size(min = 1, max = 1000)
    private List<@Valid Row> rows = new ArrayList<>();

    @Data
    public static class Row
    {
        @NotNull
        private Integer rowNumber;
        @NotNull
        @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
        private String date;
        @NotNull
        @Pattern(regexp = "^(BUY|SELL)$", message = "must be BUY or SELL")
        private String type;
        @NotBlank
        private String ticker;
        @NotNull
        @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 4)
        private String quantity;
        @NotNull
        @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
        private String price;
        @NotNull
        @ValidBigDecimal(integerConstraint = 3, decimalConstraint = 2)
        private String fees;
        @NotNull
        @ValueOfEnum(enumClass = Portfolio.class)
        private String portfolio;
    }
}
