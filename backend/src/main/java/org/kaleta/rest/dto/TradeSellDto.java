package org.kaleta.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValidId;

import java.util.ArrayList;
import java.util.List;

@Data
public class TradeSellDto
{
    @NotNull
    @ValidId
    private Long companyId;
    @NotNull
    @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d-\\d\\d$", message = "must match YYYY-MM-DD")
    private String date;
    @NotNull
    @ValidBigDecimal(integerConstraint = 6, decimalConstraint = 4)
    private String price;
    @NotNull
    @ValidBigDecimal(integerConstraint = 3, decimalConstraint = 2)
    private String fees;
    @Size(min = 1)
    private List<@Valid Trade> trades = new ArrayList<>();

    @Data
    public static class Trade
    {
        @NotNull
        @ValidId
        private Long tradeId;
        @NotNull
        @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 4)
        private String quantity;

        public Trade() {}
        public Trade(Long tradeId, String quantity)
        {
            this.tradeId = tradeId;
            this.quantity = quantity;
        }
    }
}
