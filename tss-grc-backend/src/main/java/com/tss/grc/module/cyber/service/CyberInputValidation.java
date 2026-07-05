package com.tss.grc.module.cyber.service;

import java.util.Locale;
import java.util.Set;

/**
 * CY-H — centralised, lightweight input validation for the cyber write surface.
 *
 * <p>The cyber controllers bind request bodies straight to JPA entities (there is
 * no DTO layer), so a malformed or empty body used to reach the database and
 * surface as an opaque <em>500</em> (NOT-NULL violation) or silently persist a
 * nonsense row (no FK / no enum check). These helpers turn those into clean
 * <em>400 Bad Request</em>s: every check throws {@link IllegalArgumentException},
 * which {@code GlobalExceptionHandler} already maps to 400 with the message.
 *
 * <p>Guards only ever require what the database already requires (a NOT-NULL
 * column with no default, or a real foreign key / CHECK constraint), so no
 * request that succeeds today can start failing — a body that omitted the field
 * would already have 500'd.
 */
public final class CyberInputValidation {

    private CyberInputValidation() {}

    /** Require a non-blank text field; returns it trimmed for storage. */
    public static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required and must not be blank");
        }
        return value.trim();
    }

    /** Require a positive (&gt;0) id — used for NOT-NULL foreign-key columns. */
    public static long requireId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " is required and must be a positive id");
        }
        return value;
    }

    /**
     * Validate/normalise an enum-like string against an {@code allowed} set.
     * A blank value returns {@code defaultValue} when {@code required} is false,
     * otherwise it is rejected. Non-blank values are trimmed + upper-cased and
     * must be a member of {@code allowed}, else a 400 is raised naming the set.
     */
    public static String checkEnum(String value, String field, Set<String> allowed,
                                   boolean required, String defaultValue) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new IllegalArgumentException(field + " is required and must be one of " + allowed);
            }
            return defaultValue;
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalised)) {
            throw new IllegalArgumentException(
                    field + " must be one of " + allowed + " (got '" + value + "')");
        }
        return normalised;
    }
}
