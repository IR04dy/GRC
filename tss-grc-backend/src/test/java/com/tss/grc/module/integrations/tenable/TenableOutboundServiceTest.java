package com.tss.grc.module.integrations.tenable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.tenable.client.TenableClient;
import com.tss.grc.module.integrations.tenable.config.TenableProperties;
import com.tss.grc.module.integrations.tenable.dto.RecastResponse;
import com.tss.grc.module.integrations.tenable.entity.TenableRecastLog;
import com.tss.grc.module.integrations.tenable.repository.TenableRecastLogRepository;
import com.tss.grc.module.integrations.tenable.service.TenableOutboundService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the outbound Tenable recast / accept-risk / remove-recast path:
 *   - recast happy path → recast_log row + F7 + F8 ({@code TENABLE_RECAST_APPLIED})
 *   - accept-risk happy path → expiresAt persisted + F8 ({@code TENABLE_RISK_ACCEPTED})
 *   - remove-recast happy path → no severity, F8 ({@code TENABLE_RECAST_REMOVED})
 *   - client exception → audit row persisted with error, exception re-thrown
 *   - kill-switch → no-op, no F7/F8
 *   - requested_by_user_id comes from SecurityContext
 *   - justification minimum length enforced
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenableOutboundServiceTest {

    /** Reusable justification — over the 30 char minimum. */
    private static final String GOOD_JUSTIFICATION =
        "Recasted because the affected asset sits in an isolated test VLAN — see CHANGE-2026-887";

    @Mock private TenableClient client;
    @Mock private TenableRecastLogRepository recastLogRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private TenableProperties props;
    private TenableOutboundService service;

    @BeforeEach
    void setUp() {
        props = new TenableProperties();
        props.setEnabled(true);
        service = new TenableOutboundService(
            client, props, recastLogRepo, audit, publisher, new ObjectMapper()
        );
        TenantContext.setCurrentTenant(1L);
        // Seed SecurityContext with a fake CISO — requested_by_user_id pulls from this.
        GrcUserDetails principal = new GrcUserDetails(
            888L, 1L, "ciso", "x", true, true,
            List.of(new SimpleGrantedAuthority("ROLE_CISO"))
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ── recast happy path ───────────────────────────────────────────────

    @Test
    void recastSeverity_happyPath_persistsRecastLog_andEmitsF7F8() {
        RecastResponse ok = new RecastResponse(
            "RECAST_SEVERITY", "TEN-FND-1", "asset-uuid-9", true, 200, 1, "rule-123"
        );
        when(client.recastSeverity(eq("TEN-FND-1"), eq("asset-uuid-9"), eq("LOW"), anyString())).thenReturn(ok);

        RecastResponse resp = service.recastSeverity("TEN-FND-1", "asset-uuid-9", "LOW", GOOD_JUSTIFICATION);
        assertThat(resp.accepted()).isTrue();

        ArgumentCaptor<TenableRecastLog> cap = ArgumentCaptor.forClass(TenableRecastLog.class);
        verify(recastLogRepo).save(cap.capture());
        TenableRecastLog row = cap.getValue();
        assertThat(row.getOrganisationId()).isEqualTo(1L);
        assertThat(row.getActionType()).isEqualTo("RECAST_SEVERITY");
        assertThat(row.getTargetPluginId()).isEqualTo("TEN-FND-1");
        assertThat(row.getTargetAssetUuid()).isEqualTo("asset-uuid-9");
        assertThat(row.getNewSeverity()).isEqualTo("LOW");
        assertThat(row.getRequestedByUserId()).isEqualTo(888L);
        assertThat(row.getJustification()).isEqualTo(GOOD_JUSTIFICATION);
        assertThat(row.getHttpStatus()).isEqualTo(200);
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getErrorMessage()).isNull();

        // F7 + F8 (TENABLE_RECAST_APPLIED)
        verify(audit).log(eq("TENABLE_RECAST_APPLIED"), eq("TenableFinding"), any(), anyString());
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("TENABLE_RECAST_APPLIED");
        assertThat(evt.getValue().payload()).containsEntry("tenablePluginId", "TEN-FND-1");
    }

    // ── accept-risk happy path ──────────────────────────────────────────

    @Test
    void acceptRisk_happyPath_persistsExpiresAt_andEmitsF8() {
        LocalDateTime exp = LocalDateTime.of(2026, 12, 31, 23, 59);
        RecastResponse ok = new RecastResponse(
            "ACCEPT_RISK", "TEN-FND-2", null, true, 200, 1, "rule-456"
        );
        when(client.acceptRisk(eq("TEN-FND-2"), eq(null), anyString(), eq(exp))).thenReturn(ok);

        service.acceptRisk("TEN-FND-2", null, GOOD_JUSTIFICATION, exp);

        ArgumentCaptor<TenableRecastLog> cap = ArgumentCaptor.forClass(TenableRecastLog.class);
        verify(recastLogRepo).save(cap.capture());
        assertThat(cap.getValue().getExpiresAt()).isEqualTo(exp);
        assertThat(cap.getValue().getActionType()).isEqualTo("ACCEPT_RISK");
        assertThat(cap.getValue().getNewSeverity()).isNull();

        verify(audit).log(eq("TENABLE_RISK_ACCEPTED"), eq("TenableFinding"), any(), anyString());
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("TENABLE_RISK_ACCEPTED");
    }

    // ── remove-recast happy path ────────────────────────────────────────

    @Test
    void removeRecast_happyPath_emitsRemovedDomainEvent() {
        RecastResponse ok = new RecastResponse(
            "REMOVE_RECAST", "TEN-FND-3", "asset-uuid-3", true, 200, 1, "rule-789"
        );
        when(client.removeRecast(eq("TEN-FND-3"), eq("asset-uuid-3"))).thenReturn(ok);

        service.removeRecast("TEN-FND-3", "asset-uuid-3");

        verify(audit).log(eq("TENABLE_RECAST_REMOVED"), eq("TenableFinding"), any(), anyString());
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("TENABLE_RECAST_REMOVED");

        ArgumentCaptor<TenableRecastLog> cap = ArgumentCaptor.forClass(TenableRecastLog.class);
        verify(recastLogRepo).save(cap.capture());
        // remove-recast doesn't carry a newSeverity
        assertThat(cap.getValue().getNewSeverity()).isNull();
    }

    // ── transient failure / retry ───────────────────────────────────────

    @Test
    void recastSeverity_clientThrows_persistsAuditWithError_andRethrows() {
        when(client.recastSeverity(anyString(), any(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Tenable 503 after 3 attempts"));

        assertThatThrownBy(() -> service.recastSeverity("TEN-FND-X", null, "HIGH", GOOD_JUSTIFICATION))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("503");

        ArgumentCaptor<TenableRecastLog> cap = ArgumentCaptor.forClass(TenableRecastLog.class);
        verify(recastLogRepo).save(cap.capture());
        TenableRecastLog row = cap.getValue();
        assertThat(row.getHttpStatus()).isNull();
        assertThat(row.getErrorMessage()).contains("Tenable 503");
        // No F7/F8 on a failed action
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test
    void recastSeverity_attemptCount_surfacesIntoAuditLog() {
        RecastResponse retried = new RecastResponse(
            "RECAST_SEVERITY", "TEN-FND-RR", null, true, 200, 3, "rule-rr"
        );
        when(client.recastSeverity(eq("TEN-FND-RR"), any(), eq("MEDIUM"), anyString())).thenReturn(retried);

        service.recastSeverity("TEN-FND-RR", null, "MEDIUM", GOOD_JUSTIFICATION);

        ArgumentCaptor<TenableRecastLog> cap = ArgumentCaptor.forClass(TenableRecastLog.class);
        verify(recastLogRepo).save(cap.capture());
        assertThat(cap.getValue().getAttemptCount()).isEqualTo(3);
    }

    // ── kill-switch ─────────────────────────────────────────────────────

    @Test
    void recastSeverity_whenDisabled_isNoOp_doesNotCallClient_orEmitEvents() {
        props.setEnabled(false);
        RecastResponse resp = service.recastSeverity("TEN-FND-X", null, "HIGH", GOOD_JUSTIFICATION);
        assertThat(resp.isDisabled()).isTrue();
        verify(client, never()).recastSeverity(anyString(), any(), anyString(), anyString());
        verify(recastLogRepo, never()).save(any());
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ── input validation ────────────────────────────────────────────────

    @Test
    void recastSeverity_missingPluginId_throws() {
        assertThatThrownBy(() -> service.recastSeverity(" ", null, "HIGH", GOOD_JUSTIFICATION))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recastSeverity(null, null, "HIGH", GOOD_JUSTIFICATION))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recastSeverity_shortJustification_throws() {
        assertThatThrownBy(() -> service.recastSeverity("TEN-FND-1", null, "LOW", "too short"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("30 characters");
    }

    @Test
    void acceptRisk_shortJustification_throws() {
        assertThatThrownBy(() -> service.acceptRisk("TEN-FND-1", null, "short", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("30 characters");
    }

    @Test
    void recastSeverity_invalidSeverityLabel_throws() {
        assertThatThrownBy(() -> service.recastSeverity("TEN-FND-1", null, "EXTREME", GOOD_JUSTIFICATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("newSeverity");
    }

    @Test
    void recastSeverity_tenantMissing_throws() {
        TenantContext.clear();
        RecastResponse ok = new RecastResponse(
            "RECAST_SEVERITY", "TEN-FND-1", null, true, 200, 1, "x"
        );
        when(client.recastSeverity(anyString(), any(), anyString(), anyString())).thenReturn(ok);
        assertThatThrownBy(() -> service.recastSeverity("TEN-FND-1", null, "LOW", GOOD_JUSTIFICATION))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tenant");

        verify(recastLogRepo, never()).save(any());
    }

    @Test
    void removeRecast_recordsActionAndUserOnly_noJustificationRequired() {
        RecastResponse ok = new RecastResponse(
            "REMOVE_RECAST", "TEN-FND-7", null, true, 200, 1, "rule-7"
        );
        when(client.removeRecast(eq("TEN-FND-7"), eq(null))).thenReturn(ok);

        service.removeRecast("TEN-FND-7", null);

        ArgumentCaptor<TenableRecastLog> cap = ArgumentCaptor.forClass(TenableRecastLog.class);
        verify(recastLogRepo).save(cap.capture());
        TenableRecastLog row = cap.getValue();
        assertThat(row.getActionType()).isEqualTo("REMOVE_RECAST");
        assertThat(row.getRequestedByUserId()).isEqualTo(888L);
        // System-provided justification still populated for not-null DB constraint
        assertThat(row.getJustification()).contains("REMOVE_RECAST");
    }

    // Unused param check
    @SuppressWarnings("unused")
    private static void unusedAnyLong() { anyLong(); }
}
