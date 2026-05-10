package com.tss.grc.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks the JWT_SECRET fail-fast contract.
 *
 * Historical bug: {@code application.yml} defaulted JWT_SECRET to the literal
 * "change-this-to-a-256-bit-secret-key-in-production". A prod deploy with the env
 * var unset would silently boot with a public-knowledge key. These tests prevent
 * that regression.
 */
class JwtTokenProviderTest {

    private static final String VALID_32 = "abcdefghijklmnopqrstuvwxyz123456";  // 32 chars

    @Test
    void validateSecret_accepts_a_strong_secret() {
        assertThatCode(() -> JwtTokenProvider.validateSecret(VALID_32))
            .doesNotThrowAnyException();
    }

    @Test
    void validateSecret_rejects_null() {
        assertThatThrownBy(() -> JwtTokenProvider.validateSecret(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void validateSecret_rejects_empty_string() {
        assertThatThrownBy(() -> JwtTokenProvider.validateSecret(""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void validateSecret_rejects_blank_string() {
        assertThatThrownBy(() -> JwtTokenProvider.validateSecret("   "))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateSecret_rejects_31_chars_too_short() {
        assertThatThrownBy(() -> JwtTokenProvider.validateSecret("a".repeat(31)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 characters");
    }

    @Test
    void validateSecret_accepts_exactly_32_chars() {
        assertThatCode(() -> JwtTokenProvider.validateSecret("a".repeat(32)))
            .doesNotThrowAnyException();
    }

    @Test
    void validateSecret_rejects_change_this_placeholder() {
        assertThatThrownBy(() -> JwtTokenProvider.validateSecret(
                "change-this-to-a-256-bit-secret-key-in-production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("placeholder");
    }

    @Test
    void validateSecret_rejects_CHANGE_ME_placeholder() {
        assertThatThrownBy(() -> JwtTokenProvider.validateSecret(
                "CHANGE_ME_PROVIDE_JWT_SECRET_VIA_ENVIRONMENT_VARIABLE_AT_LEAST_32_CHARS"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("placeholder");
    }

    @Test
    void validateSecret_rejects_placeholder_keyword() {
        assertThatThrownBy(() -> JwtTokenProvider.validateSecret(
                "this-is-a-placeholder-secret-and-should-be-rejected-by-the-validator"))
            .isInstanceOf(IllegalStateException.class);
    }
}
