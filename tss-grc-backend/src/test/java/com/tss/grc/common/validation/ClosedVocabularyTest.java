package com.tss.grc.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2-179 — the three ways a caller's value can be admitted, and why they are three and not one.
 *
 * <p>{@code require} / {@code orDefault} / {@code coerce} differ only in what they do with a value
 * <b>outside</b> the vocabulary, and that difference is a per-field product decision rather than a
 * style choice: refusing a whistleblower's report because its severity token was unrecognised is a
 * worse outcome than triaging it at the wrong band, and silently defaulting an ML drift severity is
 * a worse outcome than a 400. Each of the three exists because one of the sites needed exactly it.
 */
class ClosedVocabularyTest {

    private static final ClosedVocabulary SEVERITY = ClosedVocabulary.of(
        "severity", "CK_ml_drift_sev", "INFO", "MINOR", "MODERATE", "MAJOR", "CRITICAL");

    // ── the defect this class exists to make impossible ──────────────────────────────

    @Test
    @DisplayName("a lower-case token normalises to the canonical form — the whole finding")
    void lowerCaseIsNormalisedRatherThanPassedThrough() {
        assertThat(SEVERITY.require("critical")).as(
            "the column's collation is case-insensitive, so 'critical' satisfied CK_ml_drift_sev and "
            + "was stored verbatim; the Java trigger then asked \"CRITICAL\".equals(...) and got "
            + "false, so a CRITICAL drift filed no issue and left the model IN_PRODUCTION")
            .isEqualTo("CRITICAL");
        assertThat(SEVERITY.require("  Critical  ")).isEqualTo("CRITICAL");
        assertThat(SEVERITY.require("CRITICAL")).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("equalsIgnoreCase at the comparison would not have been the same fix")
    void normalisingOnWriteIsWhatMakesTheFrontendLabelResolve() {
        // The rejected alternative was one character shorter: equalsIgnoreCase at the trigger. It
        // fires the escalation but leaves 'critical' in the column, where the frontend's
        // Record<MlDriftSeverity, Label> lookup misses and the register renders a drift row with no
        // severity label at all. What this asserts is that the STORED value is canonical.
        assertThat(SEVERITY.require("critical"))
            .isEqualTo("CRITICAL")
            .isUpperCase();
    }

    // ── require ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("require: a value outside the vocabulary is a 400 that names the vocabulary")
    void requireRefusesAnUnknownTokenAndSaysWhatIsAllowed() {
        assertThatThrownBy(() -> SEVERITY.require("SEV1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("severity")
            .hasMessageContaining("SEV1")
            .hasMessageContaining("CRITICAL");
        assertThatThrownBy(() -> SEVERITY.require(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SEVERITY.require("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the message never carries anything but the caller's own value")
    void theMessageIsSafeToReturnToAnUnauthenticatedCaller() {
        // submitCase is a public portal endpoint, so this message reaches anonymous callers.
        String msg = catchMessage(() -> SEVERITY.require("SEV1"));
        assertThat(msg)
            .doesNotContain("CK_ml_drift_sev")
            .doesNotContain("ml_model_drift_observation")
            .doesNotContain("TSS_GRC");
    }

    // ── orDefault ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("orDefault: blank takes the default, unknown is still refused")
    void orDefaultDefaultsOnSilenceOnly() {
        // ⚙ The first draft of this test passed "MEDIUM" as the fallback and failed on the first run:
        // CK_ml_drift_sev's bands are INFO/MINOR/MODERATE/MAJOR/CRITICAL — there is no MEDIUM. That is
        // F2-180's 'HSE' mistake, made here by the author of the guard that caught it, which is the
        // argument for the guard: a fallback is a value the CODE chose, so nothing else examines it
        // before the database does.
        assertThat(SEVERITY.orDefault(null, "MODERATE")).isEqualTo("MODERATE");
        assertThat(SEVERITY.orDefault("", "MODERATE")).isEqualTo("MODERATE");
        assertThat(SEVERITY.orDefault("major", "MODERATE")).isEqualTo("MAJOR");
        assertThatThrownBy(() -> SEVERITY.orDefault("SEV1", "MODERATE"))
            .as("a caller who said something wrong is not the same as a caller who said nothing")
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a fallback outside the vocabulary is caught here, not at the database")
    void theFallbackMustItselfBeLegal() {
        // This is F2-180's defect in miniature: 'HSE' was the incident adapter's fallback and the
        // column had never accepted it. A fallback is a value the code chose, so it is exactly the
        // kind that reaches the CHECK unexamined.
        assertThatThrownBy(() -> SEVERITY.orDefault(null, "HSE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fallback");
        assertThatThrownBy(() -> SEVERITY.coerce("anything", "HSE"))
            .as("coerce must check its fallback even when the value is one it would have accepted")
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── coerce ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("coerce: fixes the casing without newly refusing what used to be accepted")
    void coerceKeepsTheOldFailSoftBehaviourForGenuinelyUnknownTokens() {
        assertThat(SEVERITY.coerce("critical", "MODERATE")).as(
            "this is the half that was broken: safeSeverity's case-sensitive switch read 'critical' "
            + "as unrecognised and returned the default, silently downgrading the band")
            .isEqualTo("CRITICAL");
        assertThat(SEVERITY.coerce("SEV1", "MODERATE")).as(
            "and this is the half that must NOT change — an unrecognised token was accepted as the "
            + "default before, and a whistleblower report that never arrives is worse than one "
            + "triaged at the wrong band")
            .isEqualTo("MODERATE");
        assertThat(SEVERITY.coerce(null, "MODERATE")).isEqualTo("MODERATE");
    }

    // ── is ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("is: comparing against a token the column cannot hold is always false, so it throws")
    void comparingAgainstAnImpossibleTokenIsAProgrammingError() {
        assertThat(SEVERITY.is("critical", "CRITICAL")).isTrue();
        assertThat(SEVERITY.is("  CRITICAL ", "CRITICAL")).isTrue();
        assertThat(SEVERITY.is("MAJOR", "CRITICAL")).isFalse();
        assertThat(SEVERITY.is(null, "CRITICAL")).isFalse();
        assertThatThrownBy(() -> SEVERITY.is("HIGH", "HIGH"))
            .as("'HIGH' is not in this vocabulary at all — a comparison that can only ever be false "
              + "is the F2-177 'AUDIT_COMMITTE' shape and must not fail silently")
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tokens() is immutable — a shared vocabulary that can be edited is not closed")
    void theVocabularyCannotBeMutatedByAHolder() {
        assertThatThrownBy(() -> SEVERITY.tokens().add("SEV1"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static String catchMessage(Runnable r) {
        try {
            r.run();
            throw new AssertionError("expected a refusal");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}
