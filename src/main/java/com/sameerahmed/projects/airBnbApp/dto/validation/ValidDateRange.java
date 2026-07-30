package com.sameerahmed.projects.airBnbApp.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = DateRangeValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDateRange {
    String message() default "end date must be on or after start date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String startField();

    String endField();

    /**
     * Whether the two dates may be the same day.
     *
     * <p>True for administrative windows, where closing a single date means start
     * and end are equal. False for stay ranges, where the check-out date is not
     * occupied, so equal dates describe a stay of zero nights.
     */
    boolean allowEqual() default true;
}
