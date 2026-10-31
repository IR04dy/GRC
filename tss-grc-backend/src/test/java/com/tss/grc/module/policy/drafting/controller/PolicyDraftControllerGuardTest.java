package com.tss.grc.module.policy.drafting.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.AcceptPolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.CreatePolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.RejectPolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.UpdatePolicyDraftRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDraftControllerGuardTest {

    @Test
    void controllerEnforcesComplianceModuleAndMethodPermissionsAndValidAnnotations() throws Exception {
        RequireModule mod = PolicyDraftController.class.getAnnotation(RequireModule.class);
        assertThat(mod).isNotNull();
        assertThat(mod.value()).isEqualTo("COMPLIANCE");

        assertGuard("status", "hasPermission('COMPLIANCE','READ')");
        assertGuard("list", "hasPermission('COMPLIANCE','READ')", String.class);
        assertGuard("get", "hasPermission('COMPLIANCE','READ')", Long.class);

        Method reqM = assertGuard("requestDraft", "hasPermission('COMPLIANCE','WRITE')", CreatePolicyDraftRequest.class);
        assertHasValidBody(reqM);

        Method updM = assertGuard("updateDraft", "hasPermission('COMPLIANCE','WRITE')", Long.class, UpdatePolicyDraftRequest.class);
        assertHasValidBody(updM);

        Method accM = assertGuard("acceptDraft", "hasPermission('COMPLIANCE','WRITE')", Long.class, AcceptPolicyDraftRequest.class);
        assertHasValidBody(accM);

        Method rejM = assertGuard("rejectDraft", "hasPermission('COMPLIANCE','WRITE')", Long.class, RejectPolicyDraftRequest.class);
        assertHasValidBody(rejM);
    }

    private static Method assertGuard(String name, String expectedSpel, Class<?>... params) throws Exception {
        Method m = PolicyDraftController.class.getMethod(name, params);
        PreAuthorize pre = m.getAnnotation(PreAuthorize.class);
        assertThat(pre).as("Method %s must carry @PreAuthorize", name).isNotNull();
        assertThat(pre.value()).isEqualTo(expectedSpel);
        return m;
    }

    private static void assertHasValidBody(Method m) {
        boolean foundValid = false;
        for (Parameter p : m.getParameters()) {
            if (p.isAnnotationPresent(Valid.class)) {
                foundValid = true;
                break;
            }
        }
        assertThat(foundValid).as("Method %s must validate its @RequestBody with @Valid", m.getName()).isTrue();
    }
}
