package com.tss.grc.common.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-AUTH-002 — exercises {@link StrongPassword} end-to-end through the real
 * Bean Validation engine (so the annotation defaults + validator are covered).
 */
class StrongPasswordValidatorTest {

    record Holder(@StrongPassword String pw) {}

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void close() { factory.close(); }

    private boolean ok(String pw) { return validator.validate(new Holder(pw)).isEmpty(); }

    @Test void acceptsStrongPasswords() {
        assertThat(ok("Abcdefgh1234!")).isTrue();   // 13 chars, 4 classes
        assertThat(ok("Abcdefghij12")).isTrue();     // 12 chars, lower+upper+digit (3 classes)
    }

    @Test void nullOrBlankPasses_optionalUpdateField() {
        assertThat(ok(null)).isTrue();
        assertThat(ok("")).isTrue();
    }

    @Test void rejectsTooShort() {
        assertThat(ok("Ab1!")).isFalse();            // < 12
        assertThat(ok("Admin@12345")).isFalse();     // 11 chars — the old demo password
    }

    @Test void rejectsInsufficientCharacterClasses() {
        assertThat(ok("alllowercaseletters")).isFalse(); // 1 class
        assertThat(ok("Abcdefghijklmnop")).isFalse();    // 2 classes (lower+upper)
    }
}
