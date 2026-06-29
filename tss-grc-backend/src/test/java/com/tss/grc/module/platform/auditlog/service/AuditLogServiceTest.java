package com.tss.grc.module.platform.auditlog.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditLogService — F7 builder + tenant auto-fill rule lock.
 *
 * <ul>
 *   <li>recordEvent requires actionType; severity defaults to INFO.</li>
 *   <li>Invalid severity rejected.</li>
 *   <li>Tenant id auto-filled from TenantContext; falls back to 1 (system events).</li>
 *   <li>UUID-based correlation ids generated on demand.</li>
 *   <li>Off-request (scheduler/async/event-listener): no request metadata captured,
 *       the request-scoped provider is never dereferenced.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private AuditLogRepository repo;
    @Mock private ObjectProvider<jakarta.servlet.http.HttpServletRequest> requestProvider;
    @InjectMocks private AuditLogService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    @Test void recordEvent_requiresActionType() {
        assertThatThrownBy(() -> service.recordEvent(new AuditLogService.AuditEventBuilder()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("action_type");
    }

    @Test void recordEvent_invalidSeverityRejected() {
        AuditLogService.AuditEventBuilder b = new AuditLogService.AuditEventBuilder()
            .actionType("CREATE").severity("FATAL");
        assertThatThrownBy(() -> service.recordEvent(b))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("severity");
    }

    @Test void recordEvent_defaultsSeverityToInfo() {
        when(repo.save(any())).thenAnswer(inv -> {
            AuditLogEntry e = inv.getArgument(0); e.setId(1L); return e;
        });
        AuditLogEntry saved = service.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("CREATE").entityType("Risk").entityId(99L));
        assertThat(saved.getSeverity()).isEqualTo("INFO");
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getActionType()).isEqualTo("CREATE");
    }

    @Test void recordEvent_fallsBackToTenant1WhenNoTenantBound() {
        TenantContext.clear();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditLogEntry saved = service.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("CUSTOM").message("system bootstrap"));
        assertThat(saved.getOrganisationId()).isEqualTo(1L);
    }

    @Test void log_oneLinerWorks() {
        when(repo.save(any())).thenAnswer(inv -> {
            AuditLogEntry e = inv.getArgument(0); e.setId(42L); return e;
        });
        AuditLogEntry saved = service.log("DELETE", "Control", 100L, "control retired");
        assertThat(saved.getActionType()).isEqualTo("DELETE");
        assertThat(saved.getEntityType()).isEqualTo("Control");
        assertThat(saved.getEntityId()).isEqualTo(100L);
        assertThat(saved.getMessage()).isEqualTo("control retired");
    }

    @Test void newCorrelationId_returnsUniqueValues() {
        String a = AuditLogService.newCorrelationId();
        String b = AuditLogService.newCorrelationId();
        assertThat(a).isNotBlank().hasSize(36);   // UUID format
        assertThat(b).isNotEqualTo(a);
    }

    /**
     * Regression: from a scheduler / async / event-listener thread there is no bound
     * HTTP request, so {@code RequestContextHolder.getRequestAttributes()} is null and
     * the request-scoped {@code HttpServletRequest} proxy must NOT be dereferenced —
     * touching it off-request threw "No thread-bound request found" and aborted every
     * async/scheduled audit write (the automation rule engine + SocOps / BCM /
     * compliance-deadline sweeps). The audit row is still written without request
     * metadata, and the provider is never touched (the guard short-circuits first).
     */
    @Test void recordEvent_skipsRequestMetadata_whenNoRequestBound() {
        when(repo.save(any())).thenAnswer(inv -> {
            AuditLogEntry e = inv.getArgument(0); e.setId(7L); return e;
        });

        AuditLogEntry saved = service.log("CDL_FIRE:FINDING_OVERDUE", "AuditFinding", 55L,
            "Deadline scheduler fired FINDING_OVERDUE");

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getIpAddress()).isNull();
        assertThat(saved.getSourceEndpoint()).isNull();
        verify(requestProvider, never()).getIfAvailable();   // short-circuited off-request
    }
}
