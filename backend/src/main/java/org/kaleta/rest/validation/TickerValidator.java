package org.kaleta.rest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TickerValidator implements ConstraintValidator<ValidTicker, String>
{
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (value.isEmpty()) return false;
        if (value.length() > 5) return false;
        return (value.equals(value.toUpperCase()));
    }
}
