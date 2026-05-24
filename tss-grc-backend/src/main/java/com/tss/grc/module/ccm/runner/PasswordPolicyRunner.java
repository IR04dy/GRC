package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

/** STUB — AD password-policy runner. Replaces the manual GPO screenshot review. */
@Component
public class PasswordPolicyRunner implements ControlTestRunner {

    @Override
    public String testType() { return "PASSWORD_POLICY"; }

    @Override
    public RunResult execute(ControlTestDefinition def) {
        long start = System.nanoTime();
        Map<String, Object> actual = StubRunnerSupport.baseStubPayload(def);
        boolean pass = StubRunnerSupport.deterministicPass(def.getTestCode());
        actual.put("minLength", pass ? 14 : 12);
        actual.put("complexityEnabled", true);
        int duration = StubRunnerSupport.millisSince(start);
        if (pass) {
            return RunResult.pass(StubRunnerSupport.actualJson(actual), duration);
        }
        return RunResult.fail(StubRunnerSupport.actualJson(actual),
                "Password policy below floor (stub)", duration);
    }
}
