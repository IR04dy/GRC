package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * STUB — Splunk log-retention runner. Live HEC query / index inspection
 * lands with the Splunk adapter follow-on. Until then this runner
 * reports a deterministic pass/fail.
 */
@Component
public class LogRetentionRunner implements ControlTestRunner {

    @Override
    public String testType() { return "LOG_RETENTION_DAYS"; }

    @Override
    public RunResult execute(ControlTestDefinition def) {
        long start = System.nanoTime();
        Map<String, Object> actual = StubRunnerSupport.baseStubPayload(def);
        boolean pass = StubRunnerSupport.deterministicPass(def.getTestCode());
        int simulatedDays = pass ? 400 : 280;
        actual.put("retentionDays", simulatedDays);
        int duration = StubRunnerSupport.millisSince(start);
        if (pass) {
            return RunResult.pass(StubRunnerSupport.actualJson(actual), duration);
        }
        return RunResult.fail(StubRunnerSupport.actualJson(actual),
                "Retention at " + simulatedDays + " days (stub)", duration);
    }
}
