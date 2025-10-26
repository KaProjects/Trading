package org.kaleta.rest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = BigDecimalValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBigDecimal
{
    String message() default "must be a valid BigDecimal";

    int integerConstraint() default Integer.MAX_VALUE;
    int decimalConstraint() default Integer.MAX_VALUE;

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
