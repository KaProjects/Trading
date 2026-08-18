package org.kaleta.rest.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TradeImportPreviewDto
{
    private boolean valid;
    private boolean reordered;
    private List<Row> rows = new ArrayList<>();
    private List<Error> errors = new ArrayList<>();

    @Data
    public static class Row
    {
        private Integer rowNumber;
        private String date;
        private String type;
        private String ticker;
        private String quantity;
        private String price;
        private String fees;
        private String portfolio;
        private List<Allocation> allocations = new ArrayList<>();
        private String remainingQuantity;
    }

    @Data
    public static class Allocation
    {
        private String source;
        private String purchaseDate;
        private String quantity;
    }

    @Data
    public static class Error
    {
        private Integer rowNumber;
        private String field;
        private String message;

        public Error() {}

        public Error(Integer rowNumber, String field, String message)
        {
            this.rowNumber = rowNumber;
            this.field = field;
            this.message = message;
        }
    }
}
