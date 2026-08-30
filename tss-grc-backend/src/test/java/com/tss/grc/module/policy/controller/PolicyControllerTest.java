package com.tss.grc.module.policy.controller;

import com.tss.grc.module.policy.service.PolicyManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * F2-394 — {@code linkPolicyResolution} read {@code body.get("resolutionId").toString()},
 * which NPE'd (→ opaque 500) when the key was absent, while the sibling {@code committeeId}
 * extraction in {@code referToCommittee} null-guards (F2-215 two-of-N). resolutionId is
 * required, so an absent key must be a clean 400, not a 500.
 */
@ExtendWith(MockitoExtension.class)
class PolicyControllerTest {

    @Mock PolicyManagementService service;
    @InjectMocks PolicyController controller;

    /** Discriminating: an absent resolutionId is a clean IllegalArgumentException (→ 400),
     *  not the pre-fix null.toString() NullPointerException (→ 500). */
    @Test void linkPolicyResolution_absentResolutionId_isRefusedCleanly_notNpe() {
        assertThatThrownBy(() -> controller.linkPolicyResolution(1L, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** Guard (non-discriminating): a valid resolutionId is parsed and passed to the service. */
    @Test void linkPolicyResolution_validResolutionId_parsesAndDelegates() {
        controller.linkPolicyResolution(1L, Map.of("resolutionId", "42"));
        verify(service).linkPolicyResolution(1L, 42L);
    }
}
