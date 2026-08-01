package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcmCorrectiveAction;
import com.tss.grc.module.bcm.cycle.entity.BcmGap;
import com.tss.grc.module.bcm.cycle.entity.BcmLessonLearned;
import com.tss.grc.module.bcm.cycle.service.BcmRemediationService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * BCM lessons / gaps / corrective actions REST surface — Sprint BCM-3.
 *
 * <pre>
 *   GET    /api/v1/bcm/lessons[?status=…]
 *   POST   /api/v1/bcm/lessons
 *   POST   /api/v1/bcm/lessons/:id/transition  { "to": "APPROVED" }
 *
 *   GET    /api/v1/bcm/gaps[?status=&severity=&cycleId=]
 *   POST   /api/v1/bcm/gaps
 *   POST   /api/v1/bcm/gaps/:id/transition  { "to": "ASSIGNED" }
 *
 *   GET    /api/v1/bcm/capas[?status=&gapId=]
 *   POST   /api/v1/bcm/capas
 *   PUT    /api/v1/bcm/capas/:id
 *   POST   /api/v1/bcm/capas/:id/transition  { "to": "EVIDENCE_SUBMITTED" }
 *   POST   /api/v1/bcm/capas/flag-overdue                  (scheduler-style sweep)
 *
 *   GET    /api/v1/bcm/remediation/stats
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Lessons / Gaps / CAPA (BCM-3)")
public class BcmRemediationController {

    private final BcmRemediationService service;

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    public record TransitionBody(String to) {}
    public record ReopenBody(String reason) {}

    // ── Lessons ────────────────────────────────────────────────────

    @GetMapping("/lessons")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<BcmLessonLearned> listLessons(@RequestParam(required = false) String status) {
        return service.listLessons(status);
    }

    @GetMapping("/lessons/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public BcmLessonLearned getLesson(@PathVariable Long id) { return service.getLesson(id); }

    @PostMapping("/lessons")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<BcmLessonLearned> createLesson(@RequestBody BcmLessonLearned body) {
        return ResponseEntity.ok(service.createLesson(body));
    }

    @PostMapping("/lessons/{id}/transition")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmLessonLearned transitionLesson(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transitionLesson(id, body.to(), actor());
    }

    // ── Gaps ───────────────────────────────────────────────────────

    @GetMapping("/gaps")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<BcmGap> listGaps(@RequestParam(required = false) String status,
                                 @RequestParam(required = false) String severity,
                                 @RequestParam(required = false) Long cycleId) {
        return service.listGaps(status, severity, cycleId);
    }

    @GetMapping("/gaps/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public BcmGap getGap(@PathVariable Long id) { return service.getGap(id); }

    @PostMapping("/gaps")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<BcmGap> createGap(@RequestBody BcmGap body) {
        return ResponseEntity.ok(service.createGap(body));
    }

    @PostMapping("/gaps/{id}/transition")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmGap transitionGap(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transitionGap(id, body.to(), actor());
    }

    /** Reopen a CLOSED gap — privileged (BCM_APPROVE) action; reason mandatory + audited. */
    @PostMapping("/gaps/{id}/reopen")
    @PreAuthorize("hasPermission('BCM', 'APPROVE')")
    public BcmGap reopenGap(@PathVariable Long id, @RequestBody ReopenBody body) {
        return service.reopenGap(id, body.reason(), actor());
    }

    // ── CAPAs ──────────────────────────────────────────────────────

    @GetMapping("/capas")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<BcmCorrectiveAction> listCapas(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long gapId) {
        return service.listCapas(status, gapId);
    }

    @GetMapping("/capas/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public BcmCorrectiveAction getCapa(@PathVariable Long id) { return service.getCapa(id); }

    @PostMapping("/capas")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<BcmCorrectiveAction> createCapa(@RequestBody BcmCorrectiveAction body) {
        return ResponseEntity.ok(service.createCapa(body));
    }

    @PutMapping("/capas/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmCorrectiveAction updateCapa(@PathVariable Long id, @RequestBody BcmCorrectiveAction body) {
        return service.updateCapa(id, body);
    }

    @PostMapping("/capas/{id}/transition")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmCorrectiveAction transitionCapa(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transitionCapa(id, body.to(), actor());
    }

    /** Reopen a CLOSED corrective action — privileged (BCM_APPROVE) action; reason mandatory + audited. */
    @PostMapping("/capas/{id}/reopen")
    @PreAuthorize("hasPermission('BCM', 'APPROVE')")
    public BcmCorrectiveAction reopenCapa(@PathVariable Long id, @RequestBody ReopenBody body) {
        return service.reopenCapa(id, body.reason(), actor());
    }

    @PostMapping("/capas/flag-overdue")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public Map<String, Object> flagOverdue() {
        int n = service.flagOverdueCapas();
        return Map.of("flippedToOverdue", n);
    }

    @GetMapping("/remediation/stats")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Map<String, Long> stats() { return service.stats(); }
}
