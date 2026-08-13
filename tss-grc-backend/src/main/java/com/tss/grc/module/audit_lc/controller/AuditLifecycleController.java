package com.tss.grc.module.audit_lc.controller;

import com.tss.grc.module.audit_lc.entity.AnnualPlan;
import com.tss.grc.module.audit_lc.entity.CapaTask;
import com.tss.grc.module.audit_lc.entity.WorkingPaper;
import com.tss.grc.module.audit_lc.service.AuditLifecycleService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Audit lifecycle support endpoints — annual plans, working papers,
 * CAPA tasks. The Finding state machine has its own controller at
 * {@code /api/audit-lc/findings}.
 *
 * <pre>
 *   /api/audit-lc/annual-plans                     CRUD
 *   /api/audit-lc/annual-plans/:ext/transition     POST {to}
 *   /api/audit-lc/working-papers                   CRUD (filters: engagementCode, status)
 *   /api/audit-lc/working-papers/:ext/transition   POST {to}
 *   /api/audit-lc/capas                            CRUD (filters: findingId, status)
 *   /api/audit-lc/capas/:ext/transition            POST {to}
 *   /api/audit-lc/capas/overdue-sweep              POST -> flips past-target_date CAPAs to OVERDUE
 * </pre>
 */
@RestController
@RequestMapping("/api/audit-lc")
@RequiredArgsConstructor
@RequireModule("AUDIT_LC")
@Tag(name = "Audit lifecycle — plans, working papers, CAPA")
public class AuditLifecycleController {

    private final AuditLifecycleService service;

    private static String actor(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    // ─── Annual plans ─────────────────────────────────────────────

    @GetMapping("/annual-plans")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<AnnualPlan> listAnnualPlans() { return service.listAnnualPlans(); }

    @GetMapping("/annual-plans/{externalId}")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public AnnualPlan getAnnualPlan(@PathVariable String externalId) { return service.getAnnualPlan(externalId); }

    @PostMapping("/annual-plans")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public ResponseEntity<AnnualPlan> createAnnualPlan(@RequestBody AnnualPlan body) {
        return ResponseEntity.ok(service.createAnnualPlan(body));
    }

    @PutMapping("/annual-plans/{externalId}")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public AnnualPlan updateAnnualPlan(@PathVariable String externalId, @RequestBody AnnualPlan body) {
        return service.updateAnnualPlan(externalId, body);
    }

    @PostMapping("/annual-plans/{externalId}/transition")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public AnnualPlan transitionAnnualPlan(@PathVariable String externalId,
                                           @RequestBody Map<String, String> body,
                                           Authentication auth) {
        return service.transitionAnnualPlan(externalId, body.get("to"), actor(auth));
    }

    @DeleteMapping("/annual-plans/{externalId}")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public ResponseEntity<Void> deleteAnnualPlan(@PathVariable String externalId) {
        service.deleteAnnualPlan(externalId);
        return ResponseEntity.noContent().build();
    }

    // ─── Working papers ───────────────────────────────────────────

    @GetMapping("/working-papers")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<WorkingPaper> listWorkingPapers(@RequestParam(required = false) String engagementCode,
                                                @RequestParam(required = false) String status) {
        return service.listWorkingPapers(engagementCode, status);
    }

    @GetMapping("/working-papers/{externalId}")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public WorkingPaper getWorkingPaper(@PathVariable String externalId) { return service.getWorkingPaper(externalId); }

    @PostMapping("/working-papers")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public ResponseEntity<WorkingPaper> createWorkingPaper(@RequestBody WorkingPaper body,
                                                           Authentication auth) {
        // F2-203. The author is the authenticated principal, never a body field — the same
        // rule `transitionWorkingPaper` below already applies to `reviewer`, and the reason
        // the two differed mattered: `createWorkingPaper` saved the body verbatim, and the
        // page was passing the literal string "Faisal Al-Otaibi (Auditor)". So an invented
        // auditor was PERSISTED as the author of an IIA Standard 2330 working paper, while
        // the reviewer on the very next transition was recorded correctly.
        body.setAuthor(actor(auth));
        return ResponseEntity.ok(service.createWorkingPaper(body));
    }

    @PutMapping("/working-papers/{externalId}")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public WorkingPaper updateWorkingPaper(@PathVariable String externalId, @RequestBody WorkingPaper body) {
        return service.updateWorkingPaper(externalId, body);
    }

    @PostMapping("/working-papers/{externalId}/transition")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public WorkingPaper transitionWorkingPaper(@PathVariable String externalId,
                                               @RequestBody Map<String, String> body,
                                               Authentication auth) {
        return service.transitionWorkingPaper(externalId, body.get("to"), actor(auth));
    }

    @DeleteMapping("/working-papers/{externalId}")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public ResponseEntity<Void> deleteWorkingPaper(@PathVariable String externalId) {
        service.deleteWorkingPaper(externalId);
        return ResponseEntity.noContent().build();
    }

    // ─── CAPA tasks ───────────────────────────────────────────────

    @GetMapping("/capas")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<CapaTask> listCapas(@RequestParam(required = false) String findingId,
                                    @RequestParam(required = false) String status) {
        return service.listCapas(findingId, status);
    }

    @GetMapping("/capas/{externalId}")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public CapaTask getCapa(@PathVariable String externalId) { return service.getCapa(externalId); }

    @PostMapping("/capas")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public ResponseEntity<CapaTask> createCapa(@RequestBody CapaTask body) {
        return ResponseEntity.ok(service.createCapa(body));
    }

    @PutMapping("/capas/{externalId}")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public CapaTask updateCapa(@PathVariable String externalId, @RequestBody CapaTask body) {
        return service.updateCapa(externalId, body);
    }

    @PostMapping("/capas/{externalId}/transition")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public CapaTask transitionCapa(@PathVariable String externalId, @RequestBody Map<String, String> body) {
        return service.transitionCapa(externalId, body.get("to"));
    }

    @DeleteMapping("/capas/{externalId}")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public ResponseEntity<Void> deleteCapa(@PathVariable String externalId) {
        service.deleteCapa(externalId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/capas/overdue-sweep")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public Map<String, Object> overdueSweep() {
        List<String> flipped = service.runOverdueSweep();
        return Map.of("flippedCount", flipped.size(), "flippedExternalIds", flipped);
    }
}
