package com.tss.grc.module.ccm.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.ccm.entity.ControlTestDefinition;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared helpers for STUB runners — the ones whose live integration is
 * not yet wired in (MFA enrolment, Splunk retention, password policy,
 * inactive accounts, patch level, network ACL).
 *
 * <p>Stubs MUST be deterministic for a given test code + hour bucket so
 * the dashboard tells a coherent story across screenshots. The
 * deterministic-PASS-or-FAIL decision is driven by a hash of
 * {@code (testCode, hourBucket)} — small enough variation that
 * operators see realistic-looking pass/fail churn but reproducible
 * within the same hour for screenshots and screen-sharing.
 *
 * <p>STUB runners are still gated by {@link ControlTestDefinition#getIsEnabled()}.
 * Operators must opt in to running them; the seed data ships with
 * {@code is_enabled = 0} for every test.
 */
final class StubRunnerSupport {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private StubRunnerSupport() {}

    /**
     * Deterministic PASS/FAIL decision for a stub runner. PASS biased
     * to ~80% so the dashboard is meaningful but not boring.
     */
    static boolean deterministicPass(String testCode) {
        long hourBucket = Instant.now().getEpochSecond() / 3600;
        int seed = (testCode == null ? "" : testCode).hashCode() ^ (int) hourBucket;
        // 5/5 of [0..9] → 80% pass rate.
        return Math.floorMod(seed, 10) < 8;
    }

    /** Convenience — builds a small actual-value JSON for stub responses. */
    static String actualJson(Map<String, Object> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Builds a simple "stub" marker map every stub runner adds. */
    static Map<String, Object> baseStubPayload(ControlTestDefinition def) {
        Map<String, Object> m = new HashMap<>();
        m.put("stub", true);
        m.put("testCode", def.getTestCode());
        m.put("testType", def.getTestType());
        m.put("targetSystem", def.getTargetSystemType());
        m.put("evaluatedAt", Instant.now().toString());
        return m;
    }

    static int millisSince(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000);
    }
}
