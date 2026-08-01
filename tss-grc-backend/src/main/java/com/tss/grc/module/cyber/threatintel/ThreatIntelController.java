package com.tss.grc.module.cyber.threatintel;

import com.tss.grc.module.cyber.threatintel.ThreatIntelCorrelationService.CorrelationReport;
import com.tss.grc.module.cyber.threatintel.ThreatIntelCorrelationService.CorrelationRequest;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
// Explicit bean name avoids a Spring ConflictingBeanDefinitionException with
// com.tss.grc.module.socops.controller.ThreatIntelController (same simple class
// name; Spring's auto-generated bean ids collide). Renaming the file would be
// cleaner long-term but would invalidate the recently-merged PR #166 import
// graph; the explicit name is a one-line, non-breaking equivalent.
@RestController("cyberThreatIntelController")
@RequestMapping("/api/v1/cyber/threat-intel")
@RequiredArgsConstructor
@RequireModule("CYBER")
@Tag(name = "Cyber — Threat Intelligence")
public class ThreatIntelController {

    private final ThreatIntelCorrelationService service;

    @PostMapping("/correlate")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public CorrelationReport correlate(@Valid @RequestBody CorrelationRequest body) {
        return service.correlate(body);
    }
}
