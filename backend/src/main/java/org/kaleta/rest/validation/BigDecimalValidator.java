package org.kaleta.rest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class BigDecimalValidator implements ConstraintValidator<ValidBigDecimal, String>
{
    private int integerConstraint;
    private int decimalConstraint;

    @Override
    public void initialize(ValidBigDecimal constraintAnnotation)
    {
        this.integerConstraint = constraintAnnotation.integerConstraint();
        this.decimalConstraint = constraintAnnotation.decimalConstraint();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context)
    {
        if (value == null) return true;
        if (value.isBlank()) return false;
        try {
            new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return false;
        }
        if (value.contains(".")){
            if (value.startsWith(".")) return false;
            if (value.endsWith(".")) return false;
            String[] split = value.split("\\.");
            if (split.length != 2) return false;
            if (split[0].length() > integerConstraint) return false;
            if (split[1].length() > decimalConstraint) return false;
        } else {
            if (value.length() > integerConstraint) return false;
        }
        return true;
    }
}
