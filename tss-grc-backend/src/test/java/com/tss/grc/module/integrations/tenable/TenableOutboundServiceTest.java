package com.tss.grc.module.integrations.tenable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.tenable.client.TenableClient;
import com.tss.grc.module.integrations.tenable.config.TenableProperties;
import com.tss.grc.module.integrations.tenable.dto.RecastResponse;
import com.tss.grc.module.integrations.tenable.entity.TenableFindingLog;
import com.tss.grc.module.integrations.tenable.entity.TenableRecastLog;
import com.tss.grc.module.integrations.tenable.repository.TenableFindingLogRepository;
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
import java.util.Optional;

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

    /** Canonical UUID for the optional asset scope (SEC-INT-004). */
    private static final String ASSET_UUID = "11111111-1111-1111-1111-111111111111";

    @Mock private TenableClient client;
    @Mock private TenableRecastLogRepository recastLogRepo;
    @Mock private TenableFindingLogRepository findingLogRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private TenableProperties props;
    private TenableOutboundService service;

    @BeforeEach
    void setUp() {
        props = new TenableProperties();
        props.setEnabled(true);
        service = new TenableOutboundService(
            client, props, recastLogRepo, findingLogRepo, audit, publisher, new ObjectMapper()
        );
        // SEC-INT-003 — default: the target finding is in this tenant's
        // inventory (both global-plugin and asset-pair lookups present).
        when(findingLogRepo.findFirstByOrganisationIdAndTenablePluginId(anyLong(), anyString()))
            .thenReturn(Optional.of(new TenableFindingLog()));
        when(findingLogRepo.findByOrganisationIdAndTenableAssetUuidAndTenablePluginId(
                anyLong(), anyString(), anyString()))
            .thenReturn(Optional.of(new TenableFindingLog()));
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
            "RECAST_SEVERITY", "100501", ASSET_UUID, true, 200, 1, "rule-123"
        );
        when(client.recastSeverity(eq("100501"), eq(ASSET_UUID), eq("LOW"), anyString())).thenReturn(ok);

        RecastResponse resp = service.recastSeverity("100501", ASSET_UUID, "LOW", GOOD_JUSTIFICATION);
        assertThat(resp.accepted()).isTrue();

        ArgumentCaptor<TenableRecastLog> cap = ArgumentCaptor.forClass(TenableRecastLog.class);
        verify(recastLogRepo).save(cap.capture());
        TenableRecastLog row = cap.getValue();
        assertThat(row.getOrganisationId()).isEqualTo(1L);
        assertThat(row.getActionType()).isEqualTo("RECAST_SEVERITY");
        assertThat(row.getTargetPluginId()).isEqualTo("100501");
        assertThat(row.getTargetAssetUuid()).isEqualTo(ASSET_UUID);
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
        assertThat(evt.getValue().payload()).containsEntry("tenablePluginId", "100501");
    }

    // ── accept-risk happy path ──────────────────────────────────────────

    @Test
    void acceptRisk_happyPath_persistsExpiresAt_andEmitsF8() {
        LocalDateTime exp = LocalDateTime.of(2026, 12, 31, 23, 59);
        RecastResponse ok = new RecastResponse(
            "ACCEPT_RISK", "100502", null, true, 200, 1, "rule-456"
        );
        when(client.acceptRisk(eq("100502"), eq(null), anyString(), eq(exp))).thenReturn(ok);

        service.acceptRisk("100502", null, GOOD_JUSTIFICATION, exp);

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
        String assetUuid = "33333333-3333-3333-3333-333333333333";
        RecastResponse ok = new RecastResponse(
            "REMOVE_RECAST", "100503", assetUuid, true, 200, 1, "rule-789"
        );
        when(client.removeRecast(eq("100503"), eq(assetUuid))).thenReturn(ok);

        service.removeRecast("100503", assetUuid);

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

        assertThatThrownBy(() -> service.recastSeverity("100590", null, "HIGH", GOOD_JUSTIFICATION))
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
            "RECAST_SEVERITY", "100504", null, true, 200, 3, "rule-rr"
        );
        when(client.recastSeverity(eq("100504"), any(), eq("MEDIUM"), anyString())).thenReturn(retried);

        service.recastSeverity("100504", null, "MEDIUM", GOOD_JUSTIFICATION);

        ArgumentCaptor<TenableRecastLog> cap = ArgumentCaptor.forClass(TenableRecastLog.class);
        verify(recastLogRepo).save(cap.capture());
        assertThat(cap.getValue().getAttemptCount()).isEqualTo(3);
    }

    // ── kill-switch ─────────────────────────────────────────────────────

    @Test
    void recastSeverity_whenDisabled_isNoOp_doesNotCallClient_orEmitEvents() {
        props.setEnabled(false);
        RecastResponse resp = service.recastSeverity("100590", null, "HIGH", GOOD_JUSTIFICATION);
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
    void recastSeverity_nonNumericPluginId_throws() {
        // SEC-INT-004 — plugin id must be numeric (path-injection guard).
        assertThatThrownBy(() -> service.recastSeverity("TEN-FND-1", null, "LOW", GOOD_JUSTIFICATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("numeric");
    }

    @Test
    void recastSeverity_traversalAssetUuid_throws() {
        // SEC-INT-004 — a non-UUID asset id (e.g. path traversal) is rejected.
        assertThatThrownBy(() -> service.recastSeverity("100501", "a/../../session", "LOW", GOOD_JUSTIFICATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("uuid");
    }

    @Test
    void recastSeverity_shortJustification_throws() {
        assertThatThrownBy(() -> service.recastSeverity("100501", null, "LOW", "too short"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("30 characters");
    }

    @Test
    void acceptRisk_shortJustification_throws() {
        assertThatThrownBy(() -> service.acceptRisk("100501", null, "short", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("30 characters");
    }

    @Test
    void recastSeverity_invalidSeverityLabel_throws() {
        assertThatThrownBy(() -> service.recastSeverity("100501", null, "EXTREME", GOOD_JUSTIFICATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("newSeverity");
    }

    @Test
    void recastSeverity_findingNotInTenantInventory_isDenied() {
        // SEC-INT-003 — plugin not in this tenant's finding log.
        when(findingLogRepo.findFirstByOrganisationIdAndTenablePluginId(1L, "100777"))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recastSeverity("100777", null, "LOW", GOOD_JUSTIFICATION))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(client, never()).recastSeverity(anyString(), any(), anyString(), anyString());
        verify(recastLogRepo, never()).save(any());
    }

    @Test
    void recastSeverity_tenantMissing_throws() {
        TenantContext.clear();
        RecastResponse ok = new RecastResponse(
            "RECAST_SEVERITY", "100501", null, true, 200, 1, "x"
        );
        when(client.recastSeverity(anyString(), any(), anyString(), anyString())).thenReturn(ok);
        assertThatThrownBy(() -> service.recastSeverity("100501", null, "LOW", GOOD_JUSTIFICATION))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tenant");

        verify(recastLogRepo, never()).save(any());
    }

    @Test
    void removeRecast_recordsActionAndUserOnly_noJustificationRequired() {
        RecastResponse ok = new RecastResponse(
            "REMOVE_RECAST", "100507", null, true, 200, 1, "rule-7"
        );
        when(client.removeRecast(eq("100507"), eq(null))).thenReturn(ok);

        service.removeRecast("100507", null);

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
