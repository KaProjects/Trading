package org.kaleta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.kaleta.rest.validation.ValidBigDecimal;
import org.kaleta.rest.validation.ValidUuid;

import java.util.ArrayList;
import java.util.List;

@Data
public class TradeSellDto
{
    @NotNull
    @ValidUuid
    private String companyId;
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
    @Valid
    private List<Trade> trades = new ArrayList<>();

    @Data
    public static class Trade
    {
        @NotNull
        @ValidUuid
        private String tradeId;
        @NotNull
        @ValidBigDecimal(integerConstraint = 4, decimalConstraint = 4)
        private String quantity;

        public Trade() {}
        public Trade(String tradeId, String quantity)
        {
            this.tradeId = tradeId;
            this.quantity = quantity;
        }
    }
}
