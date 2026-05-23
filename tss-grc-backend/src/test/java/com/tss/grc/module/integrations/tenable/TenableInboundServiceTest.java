package com.tss.grc.module.integrations.tenable;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.Vulnerability;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.integrations.tenable.client.TenableClient;
import com.tss.grc.module.integrations.tenable.config.TenableProperties;
import com.tss.grc.module.integrations.tenable.dto.TenableFindingDto;
import com.tss.grc.module.integrations.tenable.entity.TenableFindingLog;
import com.tss.grc.module.integrations.tenable.repository.TenableFindingLogRepository;
import com.tss.grc.module.integrations.tenable.service.TenableInboundService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
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

import java.math.BigDecimal;
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
 * Locks in the Tenable inbound poll path:
 *   - happy-path poll writes Vulnerability + finding_log + external_link
 *   - F7 + F8 emitted on every newly-ingested finding
 *   - dedupe on re-poll (same (asset_uuid, plugin_id) pair is skipped)
 *   - severity mapping covers label + CVSS v3 fallback + null default
 *   - kill-switch (props.enabled=false) short-circuits to no-op
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenableInboundServiceTest {

    @Mock private TenableClient client;
    @Mock private TenableFindingLogRepository findingLogRepo;
    @Mock private IntegrationExternalLinkRepository linkRepo;
    @Mock private VulnerabilityRepository vulnerabilities;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private TenableProperties props;
    private TenableInboundService service;

    @BeforeEach
    void setUp() {
        props = new TenableProperties();
        props.setEnabled(true);
        service = new TenableInboundService(
            client, props, findingLogRepo, linkRepo, vulnerabilities, audit, publisher
        );
        TenantContext.setCurrentTenant(1L);
        // Default: no dedupe collisions, save returns the entity unchanged but with an id.
        when(findingLogRepo.findByOrganisationIdAndTenableAssetUuidAndTenablePluginId(
                anyLong(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(vulnerabilities.save(any(Vulnerability.class))).thenAnswer(inv -> {
            Vulnerability v = inv.getArgument(0);
            v.setId(2002L);
            return v;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TenableFindingDto sampleFinding(String assetUuid, String pluginId, String label, BigDecimal cvss) {
        return new TenableFindingDto(
            assetUuid, pluginId,
            "Sample Tenable Plugin",
            "vuln-host-01.tss.local",
            label, cvss, "CVE-2024-12345",
            "2026-05-23T08:14:00.000+03:00",
            "Sample finding description"
        );
    }

    // ── severity mapping ────────────────────────────────────────────────

    @Test
    void mapTenableSeverity_labelWins_overCvss() {
        // Label wins regardless of CVSS
        assertThat(TenableInboundService.mapTenableSeverity("CRITICAL", new BigDecimal("3.0"))).isEqualTo("CRITICAL");
        assertThat(TenableInboundService.mapTenableSeverity("INFO", new BigDecimal("9.9"))).isEqualTo("INFO");
        // Case-insensitive
        assertThat(TenableInboundService.mapTenableSeverity("high", null)).isEqualTo("HIGH");
        assertThat(TenableInboundService.mapTenableSeverity("Informational", null)).isEqualTo("INFO");
    }

    @Test
    void mapTenableSeverity_cvssFallback_appliesWhenLabelMissing() {
        assertThat(TenableInboundService.mapTenableSeverity(null, new BigDecimal("9.0"))).isEqualTo("CRITICAL");
        assertThat(TenableInboundService.mapTenableSeverity(null, new BigDecimal("9.9"))).isEqualTo("CRITICAL");
        assertThat(TenableInboundService.mapTenableSeverity(null, new BigDecimal("8.9"))).isEqualTo("HIGH");
        assertThat(TenableInboundService.mapTenableSeverity(null, new BigDecimal("7.0"))).isEqualTo("HIGH");
        assertThat(TenableInboundService.mapTenableSeverity(null, new BigDecimal("6.9"))).isEqualTo("MEDIUM");
        assertThat(TenableInboundService.mapTenableSeverity(null, new BigDecimal("4.0"))).isEqualTo("MEDIUM");
        assertThat(TenableInboundService.mapTenableSeverity(null, new BigDecimal("3.9"))).isEqualTo("LOW");
        assertThat(TenableInboundService.mapTenableSeverity(null, new BigDecimal("0.1"))).isEqualTo("LOW");
        assertThat(TenableInboundService.mapTenableSeverity(null, new BigDecimal("0.0"))).isEqualTo("INFO");
        // Both missing → MEDIUM default (matches Splunk/CrowdStrike)
        assertThat(TenableInboundService.mapTenableSeverity(null, null)).isEqualTo("MEDIUM");
        assertThat(TenableInboundService.mapTenableSeverity("", null)).isEqualTo("MEDIUM");
    }

    // ── happy path ──────────────────────────────────────────────────────

    @Test
    void pollFindings_happyPath_writesVulnerability_externalLink_andEmitsF7F8() {
        when(client.pollFindings()).thenReturn(List.of(
            sampleFinding("asset-uuid-1", "plugin-99", "CRITICAL", new BigDecimal("9.8"))
        ));

        int ingested = service.pollFindings();
        assertThat(ingested).isEqualTo(1);

        // Vulnerability persisted with CRITICAL severity
        ArgumentCaptor<Vulnerability> vCap = ArgumentCaptor.forClass(Vulnerability.class);
        verify(vulnerabilities).save(vCap.capture());
        Vulnerability saved = vCap.getValue();
        assertThat(saved.getSeverity()).isEqualTo("CRITICAL");
        assertThat(saved.getCvssScore()).isEqualTo(new BigDecimal("9.8"));
        assertThat(saved.getCveId()).isEqualTo("CVE-2024-12345");
        assertThat(saved.getExternalId()).isEqualTo("TEN:asset-uuid-1:plugin-99");
        assertThat(saved.getAssetName()).isEqualTo("vuln-host-01.tss.local");
        assertThat(saved.getVulnerabilityType()).isEqualTo("TENABLE");

        // finding_log row persisted
        ArgumentCaptor<TenableFindingLog> logCap = ArgumentCaptor.forClass(TenableFindingLog.class);
        verify(findingLogRepo).save(logCap.capture());
        TenableFindingLog logRow = logCap.getValue();
        assertThat(logRow.getTenableAssetUuid()).isEqualTo("asset-uuid-1");
        assertThat(logRow.getTenablePluginId()).isEqualTo("plugin-99");
        assertThat(logRow.getSeverity()).isEqualTo("CRITICAL");
        assertThat(logRow.getCveId()).isEqualTo("CVE-2024-12345");
        assertThat(logRow.getVulnerabilityFindingId()).isEqualTo(2002L);
        assertThat(logRow.getHttpStatus()).isEqualTo(200);

        // external_link row written with integration='TENABLE'
        ArgumentCaptor<IntegrationExternalLink> linkCap = ArgumentCaptor.forClass(IntegrationExternalLink.class);
        verify(linkRepo).save(linkCap.capture());
        IntegrationExternalLink link = linkCap.getValue();
        assertThat(link.getIntegration()).isEqualTo("TENABLE");
        assertThat(link.getGrcModule()).isEqualTo("VULNERABILITY");
        assertThat(link.getGrcRecordId()).isEqualTo("asset-uuid-1|plugin-99");
        assertThat(link.getLastKnownState()).isEqualTo("OPEN");

        // F7
        verify(audit).log(eq("TENABLE_FINDING_INGESTED"), eq("Vulnerability"), eq(2002L), anyString());
        // F8
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        DomainEvent ev = evt.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("TENABLE_FINDING_INGESTED");
        assertThat(ev.payload()).containsEntry("severity", "CRITICAL")
                                .containsEntry("tenablePluginId", "plugin-99")
                                .containsEntry("tenableAssetUuid", "asset-uuid-1");
    }

    // ── dedupe ──────────────────────────────────────────────────────────

    @Test
    void pollFindings_dedupesOnAssetPluginPair_doesNotReingest() {
        when(client.pollFindings()).thenReturn(List.of(
            sampleFinding("asset-X", "plugin-Y", "HIGH", new BigDecimal("7.5"))
        ));
        TenableFindingLog already = new TenableFindingLog();
        already.setTenableAssetUuid("asset-X");
        already.setTenablePluginId("plugin-Y");
        when(findingLogRepo.findByOrganisationIdAndTenableAssetUuidAndTenablePluginId(
                1L, "asset-X", "plugin-Y"))
            .thenReturn(Optional.of(already));

        int ingested = service.pollFindings();

        assertThat(ingested).isZero();
        verify(vulnerabilities, never()).save(any());
        verify(findingLogRepo, never()).save(any());
        verify(linkRepo, never()).save(any());
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ── kill-switch ─────────────────────────────────────────────────────

    @Test
    void pollFindings_whenDisabled_isNoOp() {
        props.setEnabled(false);
        int ingested = service.pollFindings();
        assertThat(ingested).isZero();
        verify(client, never()).pollFindings();
        verify(vulnerabilities, never()).save(any());
    }

    // ── severity → vulnerability column mapping ─────────────────────────

    @Test
    void pollFindings_severityLabelsMapToCorrectVulnerabilityRows() {
        when(client.pollFindings()).thenReturn(List.of(
            sampleFinding("a", "p-1", "CRITICAL", new BigDecimal("9.8")),
            sampleFinding("a", "p-2", "HIGH",     new BigDecimal("7.5")),
            sampleFinding("a", "p-3", "MEDIUM",   new BigDecimal("5.5")),
            sampleFinding("a", "p-4", "LOW",      new BigDecimal("2.5")),
            sampleFinding("a", "p-5", "INFO",     new BigDecimal("0.0"))
        ));

        int ingested = service.pollFindings();
        assertThat(ingested).isEqualTo(5);

        ArgumentCaptor<Vulnerability> cap = ArgumentCaptor.forClass(Vulnerability.class);
        verify(vulnerabilities, org.mockito.Mockito.times(5)).save(cap.capture());
        List<Vulnerability> all = cap.getAllValues();
        assertThat(all.get(0).getSeverity()).isEqualTo("CRITICAL");
        assertThat(all.get(1).getSeverity()).isEqualTo("HIGH");
        assertThat(all.get(2).getSeverity()).isEqualTo("MEDIUM");
        assertThat(all.get(3).getSeverity()).isEqualTo("LOW");
        assertThat(all.get(4).getSeverity()).isEqualTo("INFO");
    }

    @Test
    void pollFindings_cvssFallback_appliesWhenSeverityLabelMissing() {
        when(client.pollFindings()).thenReturn(List.of(
            sampleFinding("a", "p-1", null, new BigDecimal("9.5")),
            sampleFinding("a", "p-2", "",   new BigDecimal("4.0"))
        ));
        int ingested = service.pollFindings();
        assertThat(ingested).isEqualTo(2);

        ArgumentCaptor<Vulnerability> cap = ArgumentCaptor.forClass(Vulnerability.class);
        verify(vulnerabilities, org.mockito.Mockito.times(2)).save(cap.capture());
        assertThat(cap.getAllValues().get(0).getSeverity()).isEqualTo("CRITICAL");
        assertThat(cap.getAllValues().get(1).getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    void pollFindings_emptyBatchIsNoOp() {
        when(client.pollFindings()).thenReturn(List.of());
        int ingested = service.pollFindings();
        assertThat(ingested).isZero();
        verify(vulnerabilities, never()).save(any());
    }

    @Test
    void pollFindings_tenantMissing_throws() {
        TenantContext.clear();
        when(client.pollFindings()).thenReturn(List.of(sampleFinding("a", "p", "HIGH", new BigDecimal("8.0"))));
        try {
            service.pollFindings();
            assertThat(false).as("expected IllegalStateException").isTrue();
        } catch (IllegalStateException ex) {
            assertThat(ex).hasMessageContaining("tenant");
        }
    }
}
