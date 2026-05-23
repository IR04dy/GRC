package com.tss.grc.module.integrations.crowdstrike;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.crowdstrike.client.CrowdStrikeClient;
import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
import com.tss.grc.module.integrations.crowdstrike.dto.ContainmentResponse;
import com.tss.grc.module.integrations.crowdstrike.entity.CrowdStrikeActionLog;
import com.tss.grc.module.integrations.crowdstrike.repository.CrowdStrikeActionLogRepository;
import com.tss.grc.module.integrations.crowdstrike.service.CrowdStrikeOutboundService;
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
 * Verifies the outbound containment path:
 *   - contain happy path → action_log row + F7 + F8 ({@code CROWDSTRIKE_HOST_CONTAINED})
 *   - lift happy path → F8 {@code CROWDSTRIKE_CONTAINMENT_LIFTED}
 *   - client exception → audit row persisted with error, exception re-thrown
 *   - retry attemptCount surfaces into action_log
 *   - kill-switch → no-op, no F7/F8
 *   - requested_by_user_id comes from SecurityContext
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrowdStrikeOutboundServiceTest {

    @Mock private CrowdStrikeClient client;
    @Mock private CrowdStrikeActionLogRepository actionLogRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private CrowdStrikeProperties props;
    private CrowdStrikeOutboundService service;

    @BeforeEach
    void setUp() {
        props = new CrowdStrikeProperties();
        props.setEnabled(true);
        service = new CrowdStrikeOutboundService(
            client, props, actionLogRepo, audit, publisher, new ObjectMapper()
        );
        TenantContext.setCurrentTenant(1L);
        // Seed SecurityContext with a fake user — requested_by_user_id pulls from this.
        GrcUserDetails principal = new GrcUserDetails(
            777L, 1L, "soc.tier2", "x", true, true,
            List.of(new SimpleGrantedAuthority("ROLE_SOC_TIER_2"))
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

    // ── contain happy path ──────────────────────────────────────────────

    @Test
    void containHost_happyPath_persistsActionLog_andEmitsF7F8() {
        ContainmentResponse ok = new ContainmentResponse(
            "CONTAIN_HOST", "aid-host-1", true, 202, 1, "falcon-action-contain-aid-host-1"
        );
        when(client.containHost(eq("aid-host-1"), anyString())).thenReturn(ok);

        ContainmentResponse resp = service.containHost("aid-host-1", "ransomware on PCI server");

        assertThat(resp.accepted()).isTrue();

        // action_log captures all the right fields
        ArgumentCaptor<CrowdStrikeActionLog> cap = ArgumentCaptor.forClass(CrowdStrikeActionLog.class);
        verify(actionLogRepo).save(cap.capture());
        CrowdStrikeActionLog row = cap.getValue();
        assertThat(row.getOrganisationId()).isEqualTo(1L);
        assertThat(row.getActionType()).isEqualTo("CONTAIN_HOST");
        assertThat(row.getTargetFalconHostId()).isEqualTo("aid-host-1");
        assertThat(row.getRequestedByUserId()).isEqualTo(777L);
        assertThat(row.getReasonText()).isEqualTo("ransomware on PCI server");
        assertThat(row.getHttpStatus()).isEqualTo(202);
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getErrorMessage()).isNull();

        // F7 + F8 (CROWDSTRIKE_HOST_CONTAINED)
        verify(audit).log(eq("CROWDSTRIKE_HOST_CONTAINED"), eq("FalconHost"), any(), anyString());
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("CROWDSTRIKE_HOST_CONTAINED");
        assertThat(evt.getValue().payload()).containsEntry("falconHostId", "aid-host-1");
    }

    // ── lift happy path ─────────────────────────────────────────────────

    @Test
    void liftContainment_happyPath_emitsLiftedDomainEvent() {
        ContainmentResponse ok = new ContainmentResponse(
            "LIFT_CONTAINMENT", "aid-host-2", true, 202, 1, "falcon-action-lift-aid-host-2"
        );
        when(client.liftContainment(eq("aid-host-2"))).thenReturn(ok);

        service.liftContainment("aid-host-2");

        verify(audit).log(eq("CROWDSTRIKE_CONTAINMENT_LIFTED"), eq("FalconHost"), any(), anyString());
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("CROWDSTRIKE_CONTAINMENT_LIFTED");
    }

    // ── transient failure / retry ───────────────────────────────────────

    @Test
    void containHost_clientThrows_persistsAuditWithError_andRethrows() {
        when(client.containHost(anyString(), anyString()))
            .thenThrow(new RuntimeException("Falcon 503 after 3 attempts"));

        assertThatThrownBy(() -> service.containHost("aid-host-9", "boom"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("503");

        ArgumentCaptor<CrowdStrikeActionLog> cap = ArgumentCaptor.forClass(CrowdStrikeActionLog.class);
        verify(actionLogRepo).save(cap.capture());
        CrowdStrikeActionLog row = cap.getValue();
        assertThat(row.getHttpStatus()).isNull();
        assertThat(row.getErrorMessage()).contains("Falcon 503");
        // No F7/F8 on a failed action
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test
    void containHost_attemptCount_surfacesIntoAuditLog() {
        ContainmentResponse retried = new ContainmentResponse(
            "CONTAIN_HOST", "aid-host-3", true, 202, 3, "falcon-action-contain-aid-host-3"
        );
        when(client.containHost(eq("aid-host-3"), anyString())).thenReturn(retried);

        service.containHost("aid-host-3", "lateral movement detected");

        ArgumentCaptor<CrowdStrikeActionLog> cap = ArgumentCaptor.forClass(CrowdStrikeActionLog.class);
        verify(actionLogRepo).save(cap.capture());
        assertThat(cap.getValue().getAttemptCount()).isEqualTo(3);
    }

    // ── kill-switch ─────────────────────────────────────────────────────

    @Test
    void containHost_whenDisabled_isNoOp_doesNotCallClient_orEmitEvents() {
        props.setEnabled(false);
        ContainmentResponse resp = service.containHost("aid-x", "skipped");
        assertThat(resp.isDisabled()).isTrue();
        verify(client, never()).containHost(anyString(), anyString());
        verify(actionLogRepo, never()).save(any());
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ── input validation ────────────────────────────────────────────────

    @Test
    void containHost_missingHostId_throws() {
        assertThatThrownBy(() -> service.containHost(" ", "no host"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.containHost(null, "no host"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containHost_tenantMissing_throws() {
        TenantContext.clear();
        ContainmentResponse ok = new ContainmentResponse(
            "CONTAIN_HOST", "aid-host-1", true, 202, 1, "x"
        );
        when(client.containHost(anyString(), anyString())).thenReturn(ok);
        assertThatThrownBy(() -> service.containHost("aid-host-1", "x"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tenant");

        verify(actionLogRepo, never()).save(any());
    }

    @Test
    void liftContainment_recordsActionAndUserOnly_noReasonRequired() {
        ContainmentResponse ok = new ContainmentResponse(
            "LIFT_CONTAINMENT", "aid-host-9", true, 202, 1, "falcon-action-lift-aid-host-9"
        );
        when(client.liftContainment(eq("aid-host-9"))).thenReturn(ok);

        service.liftContainment("aid-host-9");

        ArgumentCaptor<CrowdStrikeActionLog> cap = ArgumentCaptor.forClass(CrowdStrikeActionLog.class);
        verify(actionLogRepo).save(cap.capture());
        CrowdStrikeActionLog row = cap.getValue();
        assertThat(row.getActionType()).isEqualTo("LIFT_CONTAINMENT");
        assertThat(row.getReasonText()).isNull();
        assertThat(row.getRequestedByUserId()).isEqualTo(777L);
    }
}
