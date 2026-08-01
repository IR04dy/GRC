package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.service.CrisisEventPdfExporter;
import com.tss.grc.module.bcm.cycle.service.CrisisEventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the Crisis Event entity — independent audit Tier-1.
 * <pre>
 *   GET    /api/v1/bcm/crisis-events                  list (paginated)
 *   GET    /api/v1/bcm/crisis-events/active           open events for the crisis-room banner
 *   GET    /api/v1/bcm/crisis-events/stats            counts by status + severity
 *   GET    /api/v1/bcm/crisis-events/{id}             one event
 *   POST   /api/v1/bcm/crisis-events                  create
 *   PATCH  /api/v1/bcm/crisis-events/{id}             partial update
 *   POST   /api/v1/bcm/crisis-events/{id}/transition  {to: "ACTIVATED"}
 *   POST   /api/v1/bcm/crisis-events/{id}/activate-bcp  {bcpId: 42}
 *   POST   /api/v1/bcm/crisis-events/{id}/activate-drp  {drpId: 17}
 *
 *   GET    /api/v1/bcm/crisis-events/{id}/sitreps
 *   POST   /api/v1/bcm/crisis-events/{id}/sitreps
 *
 *   GET    /api/v1/bcm/crisis-events/{id}/decisions
 *   POST   /api/v1/bcm/crisis-events/{id}/decisions
 *
 *   GET    /api/v1/bcm/crisis-events/{id}/regulatory-notifications
 *   POST   /api/v1/bcm/crisis-events/{id}/regulatory-notifications
 *   POST   /api/v1/bcm/crisis-events/{id}/regulatory-notifications/{notifId}/acknowledge
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm/crisis-events")
@RequiredArgsConstructor
@Tag(name = "BCM Crisis Events")
public class CrisisEventController {

    public record TransitionBody(String to) {}
    public record ActivateBcpBody(Long bcpId) {}
    public record ActivateDrpBody(Long drpId) {}
    public record AckBody(String reference) {}

    private final CrisisEventService     service;
    private final CrisisEventPdfExporter pdfExporter;

    @GetMapping
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Page<CrisisEvent> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        return service.list(page, Math.min(size, 200));
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<CrisisEvent> active() { return service.listActive(); }

    /**
     * Slice 9-F — mobile-optimised summary of active crises with the
     * shortest regulatory deadline pre-computed. Restricted to
     * commander-tier roles because the {@code nextActionLabel} and tier
     * fields are part of the live command loop, not the wider read view.
     */
    @GetMapping("/active-summary")
    @PreAuthorize("hasAnyRole('CRISIS_COMMANDER','CISO','BCM_MANAGER','PLATFORM_ADMIN','INCIDENT_COMMANDER')")
    public List<CrisisEventService.ActiveCrisisSummaryRow> activeSummary() {
        return service.getActiveCrisisSummary();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Map<String, Long> stats() { return service.stats(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public CrisisEvent get(@PathVariable Long id) { return service.get(id); }

    /**
     * Tier-2 #12 — PDF export of the crisis event as an after-action report.
     * Includes the SitReps timeline, decisions log, and regulator notifications.
     */
    // F2-61: EXPORT is composed with READ (ADR-017 D-10c). The after-action report carries the
    // SitRep timeline, the decisions log and the regulator notifications for a live crisis — the
    // narrowest reading of "may not view a crisis record" cannot admit taking all three as a file.
    @GetMapping("/{id}/export.pdf")
    @PreAuthorize("hasPermission('BCM','EXPORT') and hasPermission('BCM','READ')")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id,
                                            @RequestParam(defaultValue = "en") String lang) {
        CrisisEvent e = service.get(id);
        byte[] pdf = pdfExporter.export(id, lang);
        String filename = (e.getEventCode() == null ? "crisis-" + id : e.getEventCode()) + ".pdf";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(pdf);
    }

    @PostMapping
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<CrisisEvent> create(@Valid @RequestBody CrisisEvent body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public CrisisEvent update(@PathVariable Long id, @RequestBody CrisisEvent body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public CrisisEvent transition(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transition(id, body.to());
    }

    @PostMapping("/{id}/activate-bcp")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public CrisisEvent activateBcp(@PathVariable Long id, @RequestBody ActivateBcpBody body) {
        return service.activateBcp(id, body.bcpId());
    }

    @PostMapping("/{id}/activate-drp")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public CrisisEvent activateDrp(@PathVariable Long id, @RequestBody ActivateDrpBody body) {
        return service.activateDrp(id, body.drpId());
    }

    // ─── SitReps ──────────────────────────────────────────────

    @GetMapping("/{id}/sitreps")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<CrisisSituationReport> listSitReps(@PathVariable Long id) {
        return service.listSitReps(id);
    }

    @PostMapping("/{id}/sitreps")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<CrisisSituationReport> addSitRep(@PathVariable Long id,
                                                           @Valid @RequestBody CrisisSituationReport body) {
        return ResponseEntity.ok(service.addSitRep(id, body));
    }

    // ─── Decisions ────────────────────────────────────────────

    @GetMapping("/{id}/decisions")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<CrisisDecision> listDecisions(@PathVariable Long id) {
        return service.listDecisions(id);
    }

    @PostMapping("/{id}/decisions")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<CrisisDecision> addDecision(@PathVariable Long id,
                                                      @Valid @RequestBody CrisisDecision body) {
        return ResponseEntity.ok(service.addDecision(id, body));
    }

    // ─── Regulatory notifications ─────────────────────────────

    @GetMapping("/{id}/regulatory-notifications")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<CrisisRegulatoryNotification> listRegNotifs(@PathVariable Long id) {
        return service.listRegulatoryNotifications(id);
    }

    @PostMapping("/{id}/regulatory-notifications")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<CrisisRegulatoryNotification> logRegNotif(@PathVariable Long id,
                                                                    @Valid @RequestBody CrisisRegulatoryNotification body) {
        return ResponseEntity.ok(service.logRegulatoryNotification(id, body));
    }

    @PostMapping("/{id}/regulatory-notifications/{notifId}/acknowledge")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public CrisisRegulatoryNotification ack(@PathVariable Long id,
                                             @PathVariable Long notifId,
                                             @RequestBody AckBody body) {
        return service.recordAcknowledgement(id, notifId, body.reference());
    }
}
