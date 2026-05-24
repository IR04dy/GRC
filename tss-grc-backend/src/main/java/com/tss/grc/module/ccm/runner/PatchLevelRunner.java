package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

/** STUB — CrowdStrike-backed patch-level runner. Live adapter follow-on. */
@Component
public class PatchLevelRunner implements ControlTestRunner {

    @Override
    public String testType() { return "PATCH_LEVEL"; }

    @Override
    public RunResult execute(ControlTestDefinition def) {
        long start = System.nanoTime();
        Map<String, Object> actual = StubRunnerSupport.baseStubPayload(def);
        boolean pass = StubRunnerSupport.deterministicPass(def.getTestCode());
        int pct = pass ? 96 : 88;
        actual.put("percentCurrent", pct);
        int duration = StubRunnerSupport.millisSince(start);
        if (pass) {
            return RunResult.pass(StubRunnerSupport.actualJson(actual), duration);
        }
        return RunResult.fail(StubRunnerSupport.actualJson(actual),
                "Endpoints at current patch level: " + pct + "% (stub)", duration);
    }
}
