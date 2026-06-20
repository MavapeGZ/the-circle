package com.thecircle.catalog.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Validates an optional article image carried as a base64 string (optionally a
 * {@code data:image/...;base64,} data URI). Accepts only real PNG or JPG content
 * (verified by magic bytes, not by the declared MIME), bounded in size. Null or
 * blank is allowed — the image is optional.
 */
@Documented
@Constraint(validatedBy = Base64ImageValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface Base64Image {
    String message() default "must be a valid PNG or JPG image under 2 MB";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
