package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.CryptoAsset;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.AppsecFindingRepository;
import com.tss.grc.module.socops.repository.CryptoAssetRepository;
import com.tss.grc.module.socops.repository.CyberInsuranceRepository;
import com.tss.grc.module.socops.repository.DlpEventRepository;
import com.tss.grc.module.socops.repository.ForensicsArtefactRepository;
import com.tss.grc.module.socops.repository.IamJmlEventRepository;
import com.tss.grc.module.socops.repository.PentestEngagementRepository;
import com.tss.grc.module.socops.repository.RegulatorExaminationRepository;
import com.tss.grc.module.socops.repository.SeasonalOverlayRepository;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the SOC ops scheduled sweep. Mocks all 10 repositories +
 * publisher + audit, feeds in single-row scenarios for each sweep so the
 * trigger selection logic, payload shape, and idempotency dedup can be
 * exercised in isolation.
 *
 * <p>The fan-out is broad — one method per sweep × the dedup case — so we
 * keep each test focused on one signal at a time. The rest of the repos
 * return empty lists by default (Mockito's stubbing strictness is set to
 * LENIENT so unused stubs don't fail the build).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SocOpsScheduledSweepTest {

    @Mock SecurityEventRepository       securityEvents;
    @Mock RegulatorExaminationRepository regulatorExams;
    @Mock CyberInsuranceRepository      cyberInsurance;
    @Mock IamJmlEventRepository         iamJml;
    @Mock CryptoAssetRepository         cryptoAssets;
    @Mock PentestEngagementRepository   pentests;
    @Mock SeasonalOverlayRepository     seasonalOverlays;
    @Mock ForensicsArtefactRepository   forensics;
    @Mock AppsecFindingRepository       appsec;
    @Mock DlpEventRepository            dlp;
    @Mock AuditLogRepository            auditRepo;
    @Mock AuditLogService               audit;
    @Mock ApplicationEventPublisher     publisher;

    @InjectMocks SocOpsScheduledSweep sweep;

    private static final Long ORG = 1L;

    @BeforeEach
    void tenant() {
        TenantContext.setCurrentTenant(ORG);
        // The orgs-to-scan list — all sweeps depend on this.
        when(securityEvents.findDistinctOrganisationIds()).thenReturn(List.of(ORG));
        // No prior audit rows by default → all triggers fire on first pass.
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                anyLong(), anyString(), anyLong())).thenReturn(List.of());
    }

    @AfterEach
    void clear() { TenantContext.clear(); }

    @Test
    void breachedSecurityEvent_firesSlaBreachedTrigger() {
        SecurityEvent e = new SecurityEvent();
        e.setId(42L);
        e.setOrganisationId(ORG);
        e.setSeverity("CRITICAL");
        e.setStatus("NEW");
        // sla_due_at one hour ago → the sweep should classify this as breached
        // rather than approaching.
        e.setSlaDueAt(LocalDateTime.now().minusHours(1));
        when(securityEvents.findApproachingSlaBreach(eq(ORG), any())).thenReturn(List.of(e));

        sweep.tick();

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        DomainEvent fired = captor.getValue();
        assertThat(fired.triggerEvent()).isEqualTo("SECURITY_EVENT_SLA_BREACHED");
        assertThat(fired.entityId()).isEqualTo(42L);
        // And we wrote an audit marker for next-run dedup.
        verify(audit).log(eq("SOC_FIRE:SECURITY_EVENT_SLA_BREACHED"),
                          eq("SecurityEvent"), eq(42L), anyString());
    }

    @Test
    void approachingSecurityEvent_firesApproachingTrigger() {
        SecurityEvent e = new SecurityEvent();
        e.setId(43L);
        e.setOrganisationId(ORG);
        e.setSeverity("HIGH");
        e.setStatus("TRIAGED");
        // sla_due_at fifteen minutes from now → approaching, not breached.
        e.setSlaDueAt(LocalDateTime.now().plusMinutes(15));
        when(securityEvents.findApproachingSlaBreach(eq(ORG), any())).thenReturn(List.of(e));

        sweep.tick();

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("SECURITY_EVENT_SLA_APPROACHING");
    }

    @Test
    void priorAuditMarker_suppressesRefire() {
        // Same breached event, but the audit log already has the marker — the
        // sweep should NOT publish a second event. Without this guard the
        // notification queue would refire every hour for as long as the event
        // sits in the breached state.
        SecurityEvent e = new SecurityEvent();
        e.setId(44L);
        e.setOrganisationId(ORG);
        e.setSeverity("CRITICAL");
        e.setStatus("NEW");
        e.setSlaDueAt(LocalDateTime.now().minusHours(1));
        when(securityEvents.findApproachingSlaBreach(eq(ORG), any())).thenReturn(List.of(e));

        AuditLogEntry prior = new AuditLogEntry();
        prior.setActionType("SOC_FIRE:SECURITY_EVENT_SLA_BREACHED");
        prior.setEntityType("SecurityEvent");
        prior.setEntityId(44L);
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                ORG, "SecurityEvent", 44L)).thenReturn(List.of(prior));

        sweep.tick();

        verify(publisher, never()).publishEvent(any());
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void expiredCert_firesExpiredTrigger() {
        CryptoAsset c = new CryptoAsset();
        c.setId(101L);
        c.setOrganisationId(ORG);
        c.setAssetCode("WEB-PROD-01");
        c.setValidUntil(LocalDateTime.now().minusDays(2));
        // Past-window range returns the expired cert; future-window returns empty
        // (we don't want it to fire both EXPIRED and EXPIRING_30D from the same tick).
        when(cryptoAssets.findExpiringBetween(eq(ORG), any(), any()))
            .thenReturn(List.of(c))      // first call — backwards 5y window
            .thenReturn(List.of());      // second call — forwards 30d window

        sweep.tick();

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        DomainEvent fired = captor.getValue();
        assertThat(fired.triggerEvent()).isEqualTo("CRYPTO_CERT_EXPIRED");
        assertThat(fired.entityId()).isEqualTo(101L);
    }

    @Test
    void noTenants_skipsAllSweeps() {
        // Empty tenant list — discoverTenants returns nothing, all per-sweep
        // calls should be skipped without touching repos or publisher.
        when(securityEvents.findDistinctOrganisationIds()).thenReturn(List.of());

        sweep.tick();

        verify(publisher, never()).publishEvent(any());
        verify(securityEvents, never()).findApproachingSlaBreach(anyLong(), any());
    }
}
