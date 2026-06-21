package com.tss.grc.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link StrongPassword}: length &gt;= minLength and at least three of
 * the four character classes (lowercase, uppercase, digit, symbol). Null/blank
 * passes so optional update fields that omit the password are unaffected.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private int minLength;

    @Override
    public void initialize(StrongPassword constraint) {
        this.minLength = constraint.minLength();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isBlank()) return true; // optional — @NotBlank enforces presence where required
        if (value.length() < minLength) return false;
        int classes = 0;
        if (value.chars().anyMatch(Character::isLowerCase)) classes++;
        if (value.chars().anyMatch(Character::isUpperCase)) classes++;
        if (value.chars().anyMatch(Character::isDigit)) classes++;
        if (value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) classes++;
        return classes >= 3;
    }
}
