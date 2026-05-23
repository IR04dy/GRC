package com.tss.grc.module.cyber.threatintel;

import com.tss.grc.module.cyber.threatintel.ThreatIntelCorrelationService.CorrelationReport;
import com.tss.grc.module.cyber.threatintel.ThreatIntelCorrelationService.CorrelationRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Threat Intelligence correlation — Sprint-8 Slice 8-D.
 *
 * <pre>
 *   POST /api/v1/cyber/threat-intel/correlate
 *   Body: CorrelationRequest (feedName, feedTimestamp, indicators[])
 *   Returns: CorrelationReport (hits[] + counts + regulatorEscalation flag)
 * </pre>
 *
 * <p>Accepts a bundle of IOCs (CVE / IP / HOSTNAME) and matches them
 * against existing tenant assets + vulnerabilities. When a hit lands
 * on a HIGH/CRITICAL-criticality entity, the response sets
 * {@code regulatorEscalation=true} — the frontend uses this to start
 * the SAMA Cyber 4h notification clock.
 */
@RestController
@RequestMapping("/api/v1/cyber/threat-intel")
@RequiredArgsConstructor
@Tag(name = "Cyber — Threat Intelligence")
public class ThreatIntelController {

    private final ThreatIntelCorrelationService service;

    @PostMapping("/correlate")
    @PreAuthorize("isAuthenticated()")
    public CorrelationReport correlate(@Valid @RequestBody CorrelationRequest body) {
        return service.correlate(body);
    }
}
