package com.tss.grc.module.integrations.crowdstrike;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.crowdstrike.client.CrowdStrikeClient;
import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
import com.tss.grc.module.integrations.crowdstrike.dto.FalconDetectionDto;
import com.tss.grc.module.integrations.crowdstrike.entity.CrowdStrikeDetectionLog;
import com.tss.grc.module.integrations.crowdstrike.repository.CrowdStrikeDetectionLogRepository;
import com.tss.grc.module.integrations.crowdstrike.service.CrowdStrikeInboundService;
import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks in the inbound poll path:
 *   - happy-path poll writes SecurityEvent + detection_log + external_link
 *   - F7 + F8 emitted on every newly-ingested detection
 *   - dedupe on re-poll (same falcon_detection_id is skipped)
 *   - severity mapping covers CRITICAL/HIGH/MEDIUM/LOW + null default
 *   - kill-switch (props.enabled=false) short-circuits to no-op
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrowdStrikeInboundServiceTest {

    @Mock private CrowdStrikeClient client;
    @Mock private CrowdStrikeDetectionLogRepository detectionLogRepo;
    @Mock private IntegrationExternalLinkRepository linkRepo;
    @Mock private SecurityEventRepository securityEvents;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    // PERF-IO-002: per-detection tx. A bare mock returns a null TransactionStatus,
    // so TransactionTemplate runs the action and commit(null) is a no-op — ingestOne
    // still executes against the mock repos.
    @Mock private PlatformTransactionManager txManager;

    private CrowdStrikeProperties props;
    private CrowdStrikeInboundService service;

    @BeforeEach
    void setUp() {
        props = new CrowdStrikeProperties();
        props.setEnabled(true);
        service = new CrowdStrikeInboundService(
            client, props, detectionLogRepo, linkRepo, securityEvents, audit, publisher, new ObjectMapper(), txManager
        );
        TenantContext.setCurrentTenant(1L);
        // Default: no dedupe collisions, save returns the entity unchanged but with an id.
        when(detectionLogRepo.findByOrganisationIdAndFalconDetectionId(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(securityEvents.save(any(SecurityEvent.class))).thenAnswer(inv -> {
            SecurityEvent ev = inv.getArgument(0);
            ev.setId(1001L);
            return ev;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private FalconDetectionDto sampleDetection(String id, Integer severity) {
        return new FalconDetectionDto(
            id, "aid-abc", "edr-host-01.tss.local",
            severity,
            "Defense Evasion", "Valid Accounts", "T1078",
            "2026-05-23T08:14:00.000+03:00",
            "Suspicious binary execution"
        );
    }

    // ── severity mapping ────────────────────────────────────────────────

    @Test
    void mapFalconSeverity_bucketsAreCorrect() {
        assertThat(CrowdStrikeInboundService.mapFalconSeverity(100)).isEqualTo("CRITICAL");
        assertThat(CrowdStrikeInboundService.mapFalconSeverity(90)).isEqualTo("CRITICAL");
        assertThat(CrowdStrikeInboundService.mapFalconSeverity(89)).isEqualTo("HIGH");
        assertThat(CrowdStrikeInboundService.mapFalconSeverity(70)).isEqualTo("HIGH");
        assertThat(CrowdStrikeInboundService.mapFalconSeverity(69)).isEqualTo("MEDIUM");
        assertThat(CrowdStrikeInboundService.mapFalconSeverity(40)).isEqualTo("MEDIUM");
        assertThat(CrowdStrikeInboundService.mapFalconSeverity(39)).isEqualTo("LOW");
        assertThat(CrowdStrikeInboundService.mapFalconSeverity(1)).isEqualTo("LOW");
        // null defaults to MEDIUM to avoid silent LOW queueing (matches Splunk)
        assertThat(CrowdStrikeInboundService.mapFalconSeverity(null)).isEqualTo("MEDIUM");
        // 0 or negative is a defensive default
        assertThat(CrowdStrikeInboundService.mapFalconSeverity(0)).isEqualTo("MEDIUM");
    }

    // ── happy path ──────────────────────────────────────────────────────

    @Test
    void pollDetections_happyPath_writesSE_externalLink_andEmitsF7F8() {
        when(client.pollDetections()).thenReturn(List.of(sampleDetection("ldt:host:1", 92)));

        int ingested = service.pollDetections();

        assertThat(ingested).isEqualTo(1);

        // SecurityEvent persisted with CRITICAL severity (92 → CRITICAL)
        ArgumentCaptor<SecurityEvent> evCap = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(securityEvents).save(evCap.capture());
        SecurityEvent saved = evCap.getValue();
        assertThat(saved.getSourceSystem()).isEqualTo("CROWDSTRIKE");
        assertThat(saved.getSeverity()).isEqualTo("CRITICAL");
        assertThat(saved.getCategory()).isEqualTo("EDR");
        assertThat(saved.getExternalId()).isEqualTo("ldt:host:1");
        assertThat(saved.getSourceHost()).isEqualTo("edr-host-01.tss.local");
        assertThat(saved.getSlaDueAt()).isNotNull();
        assertThat(saved.getRuleName()).contains("T1078");

        // detection_log row persisted
        ArgumentCaptor<CrowdStrikeDetectionLog> logCap = ArgumentCaptor.forClass(CrowdStrikeDetectionLog.class);
        verify(detectionLogRepo).save(logCap.capture());
        CrowdStrikeDetectionLog logRow = logCap.getValue();
        assertThat(logRow.getFalconDetectionId()).isEqualTo("ldt:host:1");
        assertThat(logRow.getSeverity()).isEqualTo("CRITICAL");
        assertThat(logRow.getSeverityRaw()).isEqualTo(92);
        assertThat(logRow.getSecurityEventId()).isEqualTo(1001L);
        assertThat(logRow.getHttpStatus()).isEqualTo(200);

        // external_link row written with integration='CROWDSTRIKE'
        ArgumentCaptor<IntegrationExternalLink> linkCap = ArgumentCaptor.forClass(IntegrationExternalLink.class);
        verify(linkRepo).save(linkCap.capture());
        IntegrationExternalLink link = linkCap.getValue();
        assertThat(link.getIntegration()).isEqualTo("CROWDSTRIKE");
        assertThat(link.getGrcModule()).isEqualTo("SECURITY_EVENT");
        assertThat(link.getGrcRecordId()).isEqualTo("ldt:host:1");
        assertThat(link.getLastKnownState()).isEqualTo("DETECTED");

        // F7
        verify(audit).log(eq("CROWDSTRIKE_DETECTION_INGESTED"), eq("SecurityEvent"), eq(1001L), anyString());
        // F8
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        DomainEvent ev = evt.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("CROWDSTRIKE_DETECTION_INGESTED");
        assertThat(ev.payload()).containsEntry("severity", "CRITICAL")
                                .containsEntry("falconDetectionId", "ldt:host:1");
    }

    // ── dedupe ──────────────────────────────────────────────────────────

    @Test
    void pollDetections_dedupesOnFalconDetectionId_doesNotReingest() {
        when(client.pollDetections()).thenReturn(List.of(sampleDetection("ldt:already:1", 75)));
        CrowdStrikeDetectionLog already = new CrowdStrikeDetectionLog();
        already.setFalconDetectionId("ldt:already:1");
        when(detectionLogRepo.findByOrganisationIdAndFalconDetectionId(1L, "ldt:already:1"))
            .thenReturn(Optional.of(already));

        int ingested = service.pollDetections();

        assertThat(ingested).isZero();
        verify(securityEvents, never()).save(any());
        verify(detectionLogRepo, never()).save(any());
        verify(linkRepo, never()).save(any());
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ── kill-switch ─────────────────────────────────────────────────────

    @Test
    void pollDetections_whenDisabled_isNoOp() {
        props.setEnabled(false);
        int ingested = service.pollDetections();
        assertThat(ingested).isZero();
        verify(client, never()).pollDetections();
        verify(securityEvents, never()).save(any());
    }

    // ── severity → SLA matrix ───────────────────────────────────────────

    @Test
    void pollDetections_severitiesMapToCorrectSlaWindows() {
        when(client.pollDetections()).thenReturn(List.of(
            sampleDetection("ldt:crit:1", 95),
            sampleDetection("ldt:high:2", 75),
            sampleDetection("ldt:med:3",  55),
            sampleDetection("ldt:low:4",  20)
        ));

        int ingested = service.pollDetections();
        assertThat(ingested).isEqualTo(4);

        ArgumentCaptor<SecurityEvent> cap = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(securityEvents, org.mockito.Mockito.times(4)).save(cap.capture());
        List<SecurityEvent> all = cap.getAllValues();
        assertThat(all.get(0).getSeverity()).isEqualTo("CRITICAL");
        assertThat(all.get(1).getSeverity()).isEqualTo("HIGH");
        assertThat(all.get(2).getSeverity()).isEqualTo("MEDIUM");
        assertThat(all.get(3).getSeverity()).isEqualTo("LOW");
        // SLA: CRITICAL=1h, HIGH=4h, MEDIUM=24h, LOW=72h
        long h0 = java.time.Duration.between(all.get(0).getEventTime(), all.get(0).getSlaDueAt()).toHours();
        long h1 = java.time.Duration.between(all.get(1).getEventTime(), all.get(1).getSlaDueAt()).toHours();
        long h2 = java.time.Duration.between(all.get(2).getEventTime(), all.get(2).getSlaDueAt()).toHours();
        long h3 = java.time.Duration.between(all.get(3).getEventTime(), all.get(3).getSlaDueAt()).toHours();
        assertThat(h0).isEqualTo(1);
        assertThat(h1).isEqualTo(4);
        assertThat(h2).isEqualTo(24);
        assertThat(h3).isEqualTo(72);
    }

    @Test
    void pollDetections_missingSeverityFallsBackToMedium_andSlaIs24h() {
        when(client.pollDetections()).thenReturn(List.of(
            sampleDetection("ldt:null:1", null)
        ));
        service.pollDetections();
        ArgumentCaptor<SecurityEvent> cap = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(securityEvents).save(cap.capture());
        SecurityEvent saved = cap.getValue();
        assertThat(saved.getSeverity()).isEqualTo("MEDIUM");
        long h = java.time.Duration.between(saved.getEventTime(), saved.getSlaDueAt()).toHours();
        assertThat(h).isEqualTo(24);
    }

    @Test
    void pollDetections_emptyBatchIsNoOp() {
        when(client.pollDetections()).thenReturn(List.of());
        int ingested = service.pollDetections();
        assertThat(ingested).isZero();
        verify(securityEvents, never()).save(any());
    }

    @Test
    void pollDetections_tenantMissing_throws() {
        TenantContext.clear();
        when(client.pollDetections()).thenReturn(List.of(sampleDetection("ldt:x:1", 80)));
        try {
            service.pollDetections();
            assertThat(false).as("expected IllegalStateException").isTrue();
        } catch (IllegalStateException ex) {
            assertThat(ex).hasMessageContaining("tenant");
        }
    }
}
