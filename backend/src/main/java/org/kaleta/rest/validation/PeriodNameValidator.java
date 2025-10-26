package org.kaleta.rest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.kaleta.persistence.entity.PeriodName;

import java.time.format.DateTimeParseException;

public class PeriodNameValidator implements ConstraintValidator<ValidPeriodName, String>
{
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        try {
            PeriodName.valueOf(value);
            return true;
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return false;
        }
    }
}
