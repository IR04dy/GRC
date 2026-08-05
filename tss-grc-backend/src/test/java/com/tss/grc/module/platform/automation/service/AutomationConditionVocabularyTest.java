package com.tss.grc.module.platform.automation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-118 — the automation condition evaluator supports <b>scalars only</b>, and 43 enabled rules are
 * written in an operator vocabulary it does not implement.
 *
 * <p><b>Found while writing F2-03(c)'s floor for {@code sharia}.</b> AR-253 — "HIGH Sharia audit
 * finding → escalate Audit Committee Chair" — was raised against a genuinely HIGH-severity finding
 * through the API, and the executor recorded {@code SKIPPED · Condition did not match}. Its condition
 * is {@code {"severity":{"eq":"HIGH"}}} and the producer does publish {@code severity}, so the
 * condition should have matched.
 *
 * <h3>The mechanism</h3>
 * {@link AutomationRuleEngine} compares an expected {@code JsonNode} against a payload value by
 * {@code isBoolean} / {@code isNumber} / {@code isTextual}, falling back to
 * {@code Objects.equals(expected.asText(), String.valueOf(actual))}. An operator object such as
 * {@code {"eq":"HIGH"}} is an {@code ObjectNode}: none of the three type tests match, and
 * {@code ObjectNode.asText()} returns the <b>empty string</b>. So the comparison is
 * {@code "" == "HIGH"} and the rule can never match, for any payload.
 *
 * <h3>Measured blast radius, 2026-08-05</h3>
 * <pre>
 *   automation rules ....................... 214
 *   empty condition (always matches) ....... 134
 *   scalar condition (works) ...............  37
 *   OPERATOR-OBJECT condition ..............  43   <- all enabled, 0 have ever fired
 *   operators used: eq 33 · in 3 · ">" 3 · gte 2 · gt 2 · ne 2 · lt 1
 * </pre>
 *
 * <p>⚠ <b>The silence is the dangerous part.</b> Each of those rules records
 * {@code SKIPPED · "Condition did not match"} — the same outcome a correctly-evaluated negative
 * produces. 293 SKIPPED runs exist. Nothing distinguishes "this rule considered the event and
 * declined" from "this rule cannot express its own condition".
 *
 * <p>⚠ <b>And a threshold rule that skips looks correct while being unconditional.</b> AR-255
 * ("Material SNCI > SAR 500k → escalate Board Risk Committee") skipped an 18,500 SAR event, which is
 * the right answer — for the wrong reason. A 750,000 SAR event would have skipped identically. The
 * same holds for AR-182 (fraud loss > SAR 1M), AR-185 (confirmed loss > SAR 500k) and AR-183
 * (>100 affected customers).
 *
 * <h3>Why this is a tripwire and not a fix</h3>
 * Implementing the vocabulary would arm <b>43 dormant rules at once</b>, across {@code fraud},
 * {@code opres}, {@code reginspect}, {@code regreporting} and {@code sharia}, each of which then
 * begins escalating and notifying. That is the S6c/S8e situation — an enforcement change switched on
 * at deploy acting on work already under way — and it is an owner call, not a test author's. F2-118
 * carries the fix and the arming question; this class makes the defect impossible to lose and fails
 * the moment the vocabulary is implemented, so the finding cannot be quietly superseded.
 */
class AutomationConditionVocabularyTest {

    private final AutomationRuleEngine engine = new AutomationRuleEngine(null, null, null, null);

    @Test
    @DisplayName("A scalar condition matches its payload — this half of the evaluator works")
    void scalarConditionsWork() {
        assertThat(engine.conditionMatches("{\"severity\":\"HIGH\"}", Map.of("severity", "HIGH")))
            .isTrue();
        assertThat(engine.conditionMatches("{\"severity\":\"HIGH\"}", Map.of("severity", "LOW")))
            .isFalse();
        assertThat(engine.conditionMatches("{\"contains_personal_data\":true}",
            Map.of("contains_personal_data", true))).isTrue();
        assertThat(engine.conditionMatches("{\"amount\":500000}", Map.of("amount", 500000)))
            .isTrue();
        assertThat(engine.conditionMatches("", Map.of())).as("an empty condition always matches")
            .isTrue();
    }

    /**
     * F2-118's tripwire. Every assertion here records a rule that <b>cannot fire today</b>, using the
     * exact condition JSON of a real enabled rule. When the operator vocabulary is implemented these
     * become {@code isTrue()} — and this test failing is the signal to close F2-118 and to decide,
     * deliberately, that 43 rules may now begin firing.
     */
    @Test
    @DisplayName("F2-118: every operator form silently fails to match, whatever the payload")
    void operatorConditionsCannotMatchAtAll() {
        // AR-253, sharia: HIGH finding -> Audit Committee Chair. Payload severity IS "HIGH".
        assertThat(engine.conditionMatches("{\"severity\":{\"eq\":\"HIGH\"}}",
            Map.of("severity", "HIGH")))
            .as("AR-253 cannot fire on the very case it exists for").isFalse();

        // AR-255, sharia: material SNCI. A 750k event must escalate — and does not.
        assertThat(engine.conditionMatches("{\"snciAmountSar\":{\"gt\":500000}}",
            Map.of("snciAmountSar", 750000)))
            .as("AR-255 skips a material SNCI, which is why its skip on a small one looked right")
            .isFalse();

        // AR-182, fraud: loss over SAR 1M.
        assertThat(engine.conditionMatches("{\"estimatedLossSar\":{\">\":1000000}}",
            Map.of("estimatedLossSar", 5000000))).isFalse();

        // AR-181, fraud: severity in a set.
        assertThat(engine.conditionMatches("{\"severity\":{\"in\":[\"CRITICAL\",\"HIGH\"]}}",
            Map.of("severity", "CRITICAL"))).isFalse();

        // AR-192, opres: maturity gap at or above 2.
        assertThat(engine.conditionMatches("{\"gap\":{\"gte\":2}}", Map.of("gap", 4))).isFalse();

        // AR-197, opres: average score below 3.
        assertThat(engine.conditionMatches("{\"avgCurrentScore\":{\"lt\":3.0}}",
            Map.of("avgCurrentScore", 1.5))).isFalse();

        // AR-202, reginspect: the regulator is the NCA.
        assertThat(engine.conditionMatches("{\"regulator\":{\"eq\":\"NCA\"}}",
            Map.of("regulator", "NCA"))).isFalse();

        // AR-207, regreporting: a negated comparison.
        assertThat(engine.conditionMatches("{\"overdue\":{\"ne\":\"false\"}}",
            Map.of("overdue", "true"))).isFalse();
    }

    @Test
    @DisplayName("F2-118: a malformed condition fails CLOSED, which is the one thing here that is right")
    void aBadConditionFailsClosed() {
        assertThat(engine.conditionMatches("{not json", Map.of("severity", "HIGH")))
            .as("unparseable JSON must not let a rule fire on everything").isFalse();
    }

    @Test
    @DisplayName("F2-118: a non-object condition fails OPEN, and that asymmetry is worth knowing")
    void aNonObjectConditionFailsOpen() {
        // Deliberately recorded rather than judged: `[1,2]` or `"HIGH"` as a whole condition returns
        // true, so such a rule fires on every event. No rule in either database has that shape today
        // (0 unparseable, 0 non-object of 214), but the asymmetry with the branch above — one fails
        // closed, the other fails open — is the kind of thing that decides an incident later.
        assertThat(engine.conditionMatches("[1,2]", Map.of())).isTrue();
        assertThat(engine.conditionMatches("\"HIGH\"", Map.of("severity", "LOW"))).isTrue();
    }
}
