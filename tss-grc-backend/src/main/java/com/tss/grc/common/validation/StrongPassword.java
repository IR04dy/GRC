package com.tss.grc.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SEC-AUTH-002 — password-strength constraint for the application's own user
 * store. Enforces a minimum length and character-class diversity. A null/blank
 * value passes (so it is safe on optional update fields that keep the existing
 * password); pair with {@code @NotBlank} where a password is mandatory.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
          ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default
        "Password must be at least 12 characters and include at least three of: "
        + "lowercase letter, uppercase letter, digit, symbol.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int minLength() default 12;
}
