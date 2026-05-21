package com.tss.grc.module.audit_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.dto.FindingDto;
import com.tss.grc.module.audit_lc.entity.Finding;
import com.tss.grc.module.audit_lc.repository.FindingRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Locks the audit-lifecycle finding state machine (V16 / IIA Standard 2330):
 *   OPEN                  → MGMT_RESPONSE
 *   MGMT_RESPONSE         → IN_REMEDIATION
 *   IN_REMEDIATION        → AWAITING_VERIFICATION
 *   AWAITING_VERIFICATION → CLOSED
 *   CLOSED                → (terminal)
 */
@ExtendWith(MockitoExtension.class)
class FindingServiceTest {

    private static final Long ORG_ID = 1L;
    private static final String EXT_ID = "FIN-2026-001";

    @Mock private FindingRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private FindingService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Finding finding(String status) {
        Finding f = new Finding();
        f.setExternalId(EXT_ID);
        f.setOrganisationId(ORG_ID);
        f.setStatus(status);
        f.setSeverity("HIGH");
        return f;
    }

    private static FindingDto.TransitionRequest req(String status) {
        FindingDto.TransitionRequest r = new FindingDto.TransitionRequest();
        r.setStatus(status);
        return r;
    }

    @Test
    void transition_OPEN_to_MGMT_RESPONSE_succeeds() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(finding("OPEN")));
        when(repo.save(any(Finding.class))).thenAnswer(inv -> inv.getArgument(0));

        FindingDto result = service.transition(EXT_ID, req("MGMT_RESPONSE"));

        assertThat(result.getStatus()).isEqualTo("MGMT_RESPONSE");
    }

    @Test
    void transition_AWAITING_VERIFICATION_to_CLOSED_succeeds() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(finding("AWAITING_VERIFICATION")));
        when(repo.save(any(Finding.class))).thenAnswer(inv -> inv.getArgument(0));

        FindingDto result = service.transition(EXT_ID, req("CLOSED"));

        assertThat(result.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void transition_OPEN_to_CLOSED_skips_remediation_and_rejects() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(finding("OPEN")));

        assertThatThrownBy(() -> service.transition(EXT_ID, req("CLOSED")))
            .isInstanceOf(IllegalTransitionException.class)
            .extracting("from", "to", "allowed")
            .containsExactly("OPEN", "CLOSED", Set.of("MGMT_RESPONSE"));
    }

    @Test
    void transition_CLOSED_is_terminal() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(finding("CLOSED")));

        assertThatThrownBy(() -> service.transition(EXT_ID, req("OPEN")))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void transition_unknownExternalId_throwsEntityNotFoundException() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, "FIN-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition("FIN-X", req("MGMT_RESPONSE")))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
