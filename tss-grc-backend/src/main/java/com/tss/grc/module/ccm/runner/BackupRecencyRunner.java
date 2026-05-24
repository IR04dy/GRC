package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * STUB — backup recency runner. Queries an internal {@code ops.backup_log}
 * table; today no such table exists so the implementation reports a
 * deterministic pass/fail. The shape of the actual JSON matches what
 * the production query will return so the dashboard renders correctly.
 */
@Component
public class BackupRecencyRunner implements ControlTestRunner {

    @Override
    public String testType() { return "BACKUP_RECENCY"; }

    @Override
    public RunResult execute(ControlTestDefinition def) {
        long start = System.nanoTime();
        Map<String, Object> actual = StubRunnerSupport.baseStubPayload(def);
        boolean pass = StubRunnerSupport.deterministicPass(def.getTestCode());
        int hoursSinceLast = pass ? 11 : 38;
        actual.put("hoursSinceLastSuccess", hoursSinceLast);
        int duration = StubRunnerSupport.millisSince(start);
        if (pass) {
            return RunResult.pass(StubRunnerSupport.actualJson(actual), duration);
        }
        return RunResult.fail(StubRunnerSupport.actualJson(actual),
                "Last successful backup " + hoursSinceLast + "h ago (stub)", duration);
    }
}
