package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

/** STUB — AD inactive-accounts runner. JML mover/leaver hygiene. */
@Component
public class InactiveAccountsRunner implements ControlTestRunner {

    @Override
    public String testType() { return "INACTIVE_ACCOUNTS"; }

    @Override
    public RunResult execute(ControlTestDefinition def) {
        long start = System.nanoTime();
        Map<String, Object> actual = StubRunnerSupport.baseStubPayload(def);
        boolean pass = StubRunnerSupport.deterministicPass(def.getTestCode());
        int offenders = pass ? 0 : 7;
        actual.put("inactiveOffenders", offenders);
        int duration = StubRunnerSupport.millisSince(start);
        if (pass) {
            return RunResult.pass(StubRunnerSupport.actualJson(actual), duration);
        }
        return RunResult.fail(StubRunnerSupport.actualJson(actual),
                offenders + " enabled accounts inactive > 90 days (stub)", duration);
    }
}
