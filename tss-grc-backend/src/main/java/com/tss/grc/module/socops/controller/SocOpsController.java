package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.dto.ScannerRunIngestRequest;
import com.tss.grc.module.socops.dto.SiemEventIngestRequest;
import com.tss.grc.module.socops.dto.SocOpsDashboardResponse;
import com.tss.grc.module.socops.entity.ScannerRun;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.service.SocOpsDashboardService;
import com.tss.grc.module.socops.service.SocOpsIngestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SOC Operations primary controller.
 *
 * <pre>
 *   GET  /api/v1/socops/dashboard
 *   POST /api/v1/socops/siem-events     ← SIEM webhook (vendor-neutral payload)
 *   POST /api/v1/socops/scanner-runs    ← Vulnerability scanner ingest
 * </pre>
 *
 * <p>These three endpoints are the platform's transition from a Chief Risk
 * Officer's tool to a Chief Information Security Officer's tool. The
 * dashboard answers the four questions a CISO is asked at any executive
 * meeting (MTTD, MTTR, what's open, what's approaching SLA breach); the
 * two ingest endpoints close the gap where incidents were being entered
 * manually after the fact.
 */
@RestController
@RequestMapping("/api/v1/socops")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations")
public class SocOpsController {

    private final SocOpsDashboardService dashboard;
    private final SocOpsIngestService    ingest;

    /** Leadership-ops dashboard payload — MTTD/MTTR, open by severity,
     *  SLA-breach approaching, TI/awareness/cert/regulator KPIs. */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('CISO','SECURITY_ANALYST','PLATFORM_ADMIN','RISK_MANAGER','AUDITOR')")
    public SocOpsDashboardResponse dashboard() {
        return dashboard.get();
    }

    /** SIEM event webhook. Vendor-neutral payload shape — the SIEM-side
     *  connector (Splunk add-on, QRadar app, Sentinel logic app, Logpoint
     *  webhook, etc.) maps that vendor's schema to {@link SiemEventIngestRequest}.
     *  Idempotent on ({@code sourceSystem}, {@code externalId}). */
    @PostMapping("/siem-events")
    @PreAuthorize("hasAnyAuthority('SIEM_INGEST','PLATFORM_ADMIN','CISO')")
    public ResponseEntity<SecurityEvent> ingestSiemEvent(@Valid @RequestBody SiemEventIngestRequest req) {
        return ResponseEntity.ok(ingest.ingestSiemEvent(req));
    }

    /** Vulnerability scanner run-completion webhook. Each scanner adapter
     *  (Tenable / Qualys / Defender / Rapid7 / Wiz / Prisma Cloud) POSTs
     *  the summary; detail findings load separately into the cyber.vulnerability
     *  tables. */
    @PostMapping("/scanner-runs")
    @PreAuthorize("hasAnyAuthority('SCANNER_INGEST','PLATFORM_ADMIN','CISO')")
    public ResponseEntity<ScannerRun> ingestScannerRun(@Valid @RequestBody ScannerRunIngestRequest req) {
        return ResponseEntity.ok(ingest.ingestScannerRun(req));
    }
}
