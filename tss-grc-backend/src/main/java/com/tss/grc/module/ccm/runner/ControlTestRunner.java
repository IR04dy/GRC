package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;

/**
 * Strategy for executing one CCM test.
 *
 * <p>Each test_type has a corresponding runner. The dispatch table is
 * built at startup from every Spring bean implementing this interface
 * (see {@link ControlTestRunnerRegistry}).
 *
 * <p>Implementations MUST be tolerant of error conditions — never throw
 * out of {@link #execute(ControlTestDefinition)}. Convert exceptions
 * into a {@link RunResult} with status {@code ERROR} and the message in
 * {@code failureReason}. The service layer relies on that contract to
 * keep the scheduler loop alive.
 */
public interface ControlTestRunner {

    /** Returns the {@code test_type} string this runner handles. */
    String testType();

    /** Execute the test and return the outcome. */
    RunResult execute(ControlTestDefinition def);

    /**
     * Outcome of one run.
     *
     * @param status           PASS | FAIL | ERROR | SKIPPED | TIMEOUT
     * @param actualValueJson  What the system actually returned (JSON
     *                          string) — fed into the evidence hash.
     * @param failureReason    Human-readable reason on FAIL/ERROR/TIMEOUT.
     * @param durationMs       Wall-clock duration in milliseconds.
     */
    record RunResult(String status, String actualValueJson, String failureReason, Integer durationMs) {
        public static RunResult pass(String actualJson, int durationMs) {
            return new RunResult("PASS", actualJson, null, durationMs);
        }
        public static RunResult fail(String actualJson, String reason, int durationMs) {
            return new RunResult("FAIL", actualJson, reason, durationMs);
        }
        public static RunResult error(String reason, int durationMs) {
            return new RunResult("ERROR", null, reason, durationMs);
        }
        public static RunResult skipped(String reason, int durationMs) {
            return new RunResult("SKIPPED", null, reason, durationMs);
        }
        public static RunResult timeout(int durationMs) {
            return new RunResult("TIMEOUT", null, "Test exceeded timeout_seconds budget", durationMs);
        }
    }
}
