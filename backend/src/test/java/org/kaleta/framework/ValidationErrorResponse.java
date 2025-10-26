package org.kaleta.framework;

import lombok.Data;

import java.util.List;

@Data
public class ValidationErrorResponse {
    private String title;
    private int status;
    private List<Violation> violations;

    @Data
    public static class Violation {
        private String field;
        private String message;
    }
}
