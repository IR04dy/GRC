package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

/** STUB — firewall rule inspection (Palo Alto Panorama / Cisco ASA / etc.). */
@Component
public class NetworkAclRunner implements ControlTestRunner {

    @Override
    public String testType() { return "NETWORK_ACL_RULE"; }

    @Override
    public RunResult execute(ControlTestDefinition def) {
        long start = System.nanoTime();
        Map<String, Object> actual = StubRunnerSupport.baseStubPayload(def);
        boolean pass = StubRunnerSupport.deterministicPass(def.getTestCode());
        int violations = pass ? 0 : 1;
        actual.put("violatingRules", violations);
        int duration = StubRunnerSupport.millisSince(start);
        if (pass) {
            return RunResult.pass(StubRunnerSupport.actualJson(actual), duration);
        }
        return RunResult.fail(StubRunnerSupport.actualJson(actual),
                violations + " forbidden ACL rule(s) present (stub)", duration);
    }
}
