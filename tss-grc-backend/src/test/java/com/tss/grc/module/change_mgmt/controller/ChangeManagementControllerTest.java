package com.tss.grc.module.change_mgmt.controller;

import com.tss.grc.module.change_mgmt.service.ChangeManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * F2-394 — {@code linkChangeResolution} read {@code body.get("resolutionId").toString()},
 * which NPE'd (→ opaque 500) when the key was absent, while the sibling {@code committeeId}
 * extraction in {@code referToCommittee} null-guards. resolutionId is required, so an absent
 * key must be a clean 400 (IllegalArgumentException), not a 500.
 *
 * <p>These endpoints take a raw {@code Map} rather than a bean-validated DTO on purpose:
 * {@code @Valid} fires before {@code @PreAuthorize}, so a {@code @NotNull} field would 400 an
 * unauthorized caller before the 403 — the fix is a manual null-guard after {@code @PreAuthorize}.
 */
@ExtendWith(MockitoExtension.class)
class ChangeManagementControllerTest {

    @Mock ChangeManagementService service;
    @InjectMocks ChangeManagementController controller;

    /** Discriminating: an absent resolutionId is a clean IllegalArgumentException (→ 400),
     *  not the pre-fix null.toString() NullPointerException (→ 500). */
    @Test void linkChangeResolution_absentResolutionId_isRefusedCleanly_notNpe() {
        assertThatThrownBy(() -> controller.linkChangeResolution(1L, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** Guard (non-discriminating): a valid resolutionId is parsed and passed to the service. */
    @Test void linkChangeResolution_validResolutionId_parsesAndDelegates() {
        controller.linkChangeResolution(1L, Map.of("resolutionId", "42"));
        verify(service).linkChangeResolution(1L, 42L);
    }
}
