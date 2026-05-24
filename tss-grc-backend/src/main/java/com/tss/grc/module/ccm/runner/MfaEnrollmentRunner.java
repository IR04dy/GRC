package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * STUB — MFA enrolment percentage runner. Live Azure AD integration
 * lands with the MFA enrollment workstream (Sprint 13, Open Issue #008).
 * Until then this runner reports a deterministic pass/fail based on
 * {@code testCode} + hour bucket so the dashboard has content.
 */
@Component
public class MfaEnrollmentRunner implements ControlTestRunner {

    @Override
    public String testType() { return "MFA_ENROLLMENT_PERCENTAGE"; }

    @Override
    public RunResult execute(ControlTestDefinition def) {
        long start = System.nanoTime();
        Map<String, Object> actual = StubRunnerSupport.baseStubPayload(def);
        boolean pass = StubRunnerSupport.deterministicPass(def.getTestCode());
        int simulatedPercent = pass ? 97 : 92;
        actual.put("percentEnrolled", simulatedPercent);
        int duration = StubRunnerSupport.millisSince(start);
        if (pass) {
            return RunResult.pass(StubRunnerSupport.actualJson(actual), duration);
        }
        return RunResult.fail(StubRunnerSupport.actualJson(actual),
                "MFA enrolment at " + simulatedPercent + "% (stub)", duration);
    }
}
