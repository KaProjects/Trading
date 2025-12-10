package org.kaleta.rest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ValueOfEnumValidator implements ConstraintValidator<ValueOfEnum, String>
{
    private Collection<String> acceptedValues;
    private String enumClassName;

    @Override
    public void initialize(ValueOfEnum annotation)
    {
        acceptedValues = Stream.of(annotation.enumClass().getEnumConstants()).map(Enum::name).collect(Collectors.toList());
        enumClassName =  annotation.enumClass().getSimpleName();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context)
    {
        if (value == null) return true;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("must be any of " + enumClassName).addConstraintViolation();
        return acceptedValues.contains(value);
    }
}
