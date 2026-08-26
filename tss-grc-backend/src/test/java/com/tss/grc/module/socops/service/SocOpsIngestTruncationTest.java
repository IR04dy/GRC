package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.socops.dto.ScannerRunIngestRequest;
import com.tss.grc.module.socops.dto.SiemEventIngestRequest;
import com.tss.grc.module.socops.entity.ScannerRun;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.ScannerRunRepository;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.Column;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-381 — {@code SocOpsIngestService} stored external SIEM/scanner fields RAW into bounded
 * {@code SecurityEvent}/{@code ScannerRun} columns (rule_name 300, source_host / destination_host /
 * username 255, source_ip / destination_ip 45, category 60, scanner 40, scan_name 300). A machine
 * feed sending an oversized field → SQL 8152 → opaque 500, and because {@code ingestSiemEvent} /
 * {@code ingestScannerRun} are {@code @Transactional} the whole ingestion rolled back — the security
 * event was neither recorded nor escalated. The sibling {@code CrowdStrikeInboundService} truncates
 * the identical columns; SocOps now does too, for the DESCRIPTIVE fields.
 *
 * <p>{@code externalId} / {@code sourceSystem} are the upstream system's own identity AND the dedup
 * key: they are left RAW rather than silently mangled into a colliding key (a malformed &gt;64 id is
 * a far-fetched edge, and {@code ClientSuppliedReferenceRatchetTest} recognises them as
 * upstream-owned references the platform must not rewrite). {@code severity} / {@code status} are
 * vocabulary fields left raw too (truncating a severity would corrupt the SLA-window switch).
 *
 * <p>Hermetic (Mockito, no DB — a mocked repo has no column, so a unit test cannot reproduce the
 * 8152; assert the cap the service applies before save, per F2-320 / F2-325). Column widths are read
 * by reflection so the cap literals and the columns cannot drift.
 */
@ExtendWith(MockitoExtension.class)
class SocOpsIngestTruncationTest {

    private static final Long ORG_ID = 1L;

    @Mock private SecurityEventRepository   securityEvents;
    @Mock private ScannerRunRepository      scannerRuns;
    @Mock private AuditLogService           audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private SocOpsIngestService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static int col(Class<?> c, String field) throws Exception {
        return c.getDeclaredField(field).getAnnotation(Column.class).length();
    }

    private SiemEventIngestRequest siem(String externalId, String sourceSystem, String severity,
                                        String category, String ruleName, String sourceHost,
                                        String sourceIp, String destinationHost, String destinationIp,
                                        String username) {
        return new SiemEventIngestRequest(externalId, sourceSystem, LocalDateTime.now(), severity,
            category, ruleName, sourceHost, sourceIp, destinationHost, destinationIp, username,
            null, "{\"raw\":\"payload\"}");
    }

    @Test
    void ingestSiemEvent_oversizedDescriptiveFields_cappedToTheirColumnWidths() throws Exception {
        when(securityEvents.findByOrganisationIdAndSourceSystemAndExternalId(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(securityEvents.save(any(SecurityEvent.class))).thenAnswer(inv -> {
            SecurityEvent e = inv.getArgument(0); e.setId(1L); return e;
        });

        // Every DESCRIPTIVE field is sent well over its column width; the identity fields are short
        // and severity is a valid short token.
        service.ingestSiemEvent(siem(
            "ALERT-1", "Splunk", "HIGH", "C".repeat(100), "R".repeat(400),
            "H".repeat(300), "1".repeat(60), "D".repeat(300), "2".repeat(60), "U".repeat(300)));

        ArgumentCaptor<SecurityEvent> captor = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(securityEvents).save(captor.capture());
        SecurityEvent ev = captor.getValue();

        assertThat(ev.getCategory().length()).isLessThanOrEqualTo(col(SecurityEvent.class, "category"));
        assertThat(ev.getRuleName().length()).isLessThanOrEqualTo(col(SecurityEvent.class, "ruleName"));
        assertThat(ev.getSourceHost().length()).isLessThanOrEqualTo(col(SecurityEvent.class, "sourceHost"));
        assertThat(ev.getSourceIp().length()).isLessThanOrEqualTo(col(SecurityEvent.class, "sourceIp"));
        assertThat(ev.getDestinationHost().length()).isLessThanOrEqualTo(col(SecurityEvent.class, "destinationHost"));
        assertThat(ev.getDestinationIp().length()).isLessThanOrEqualTo(col(SecurityEvent.class, "destinationIp"));
        assertThat(ev.getUsername().length()).isLessThanOrEqualTo(col(SecurityEvent.class, "username"));
        // severity is a vocabulary that feeds computeSlaDue + the CRITICAL fast-lane — left raw.
        assertThat(ev.getSeverity()).isEqualTo("HIGH");
    }

    @Test
    void ingestSiemEvent_upstreamIdentityFields_leftRawNotMangled() {
        // externalId / sourceSystem are the upstream system's own identity and the dedup key.
        // They must NOT be truncated: silently mangling an upstream id would collide the dedup key
        // and break correlation. A >column id is a malformed edge left to fail (or be @Size-rejected
        // later), never rewritten. The dedup lookup must use the RAW values, unchanged.
        when(securityEvents.findByOrganisationIdAndSourceSystemAndExternalId(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(securityEvents.save(any(SecurityEvent.class))).thenAnswer(inv -> {
            SecurityEvent e = inv.getArgument(0); e.setId(1L); return e;
        });

        service.ingestSiemEvent(siem(
            "E".repeat(100), "S".repeat(100), "HIGH", null, null, null, null, null, null, null));

        verify(securityEvents).findByOrganisationIdAndSourceSystemAndExternalId(
            eq(ORG_ID), eq("S".repeat(100)), eq("E".repeat(100)));
        ArgumentCaptor<SecurityEvent> captor = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(securityEvents).save(captor.capture());
        assertThat(captor.getValue().getExternalId()).isEqualTo("E".repeat(100));
        assertThat(captor.getValue().getSourceSystem()).isEqualTo("S".repeat(100));
    }

    @Test
    void ingestSiemEvent_shortFields_passThroughUntruncated() {
        when(securityEvents.findByOrganisationIdAndSourceSystemAndExternalId(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(securityEvents.save(any(SecurityEvent.class))).thenAnswer(inv -> {
            SecurityEvent e = inv.getArgument(0); e.setId(1L); return e;
        });

        service.ingestSiemEvent(siem(
            "ALERT-1", "Splunk", "CRITICAL", "Malware", "T1059 · PowerShell",
            "host.corp.local", "10.0.0.1", "dc.corp.local", "10.0.0.2", "jdoe"));

        ArgumentCaptor<SecurityEvent> captor = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(securityEvents).save(captor.capture());
        SecurityEvent ev = captor.getValue();
        assertThat(ev.getRuleName()).isEqualTo("T1059 · PowerShell");
        assertThat(ev.getSourceIp()).isEqualTo("10.0.0.1");
        assertThat(ev.getUsername()).isEqualTo("jdoe");
    }

    @Test
    void ingestScannerRun_oversizedDescriptiveFields_cappedToTheirColumnWidths() throws Exception {
        when(scannerRuns.save(any(ScannerRun.class))).thenAnswer(inv -> {
            ScannerRun r = inv.getArgument(0); r.setId(2L); return r;
        });

        service.ingestScannerRun(new ScannerRunIngestRequest(
            "N".repeat(100), "V".repeat(400), "S".repeat(4000), LocalDateTime.now(),
            LocalDateTime.now(), "COMPLETED", 3, 1, 2, 0, 0, 10, "{\"raw\":1}", null));

        ArgumentCaptor<ScannerRun> captor = ArgumentCaptor.forClass(ScannerRun.class);
        verify(scannerRuns).save(captor.capture());
        ScannerRun run = captor.getValue();
        assertThat(run.getScanner().length()).isLessThanOrEqualTo(col(ScannerRun.class, "scanner"));
        assertThat(run.getScanName().length()).isLessThanOrEqualTo(col(ScannerRun.class, "scanName"));
        // scope is NVARCHAR(MAX); status is a vocabulary from the request — both left uncapped.
        assertThat(run.getScope().length()).isEqualTo(4000);
        assertThat(run.getStatus()).isEqualTo("COMPLETED");
    }
}
