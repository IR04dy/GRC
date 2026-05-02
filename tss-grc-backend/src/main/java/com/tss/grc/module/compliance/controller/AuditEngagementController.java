package com.tss.grc.module.compliance.controller;

import com.tss.grc.module.compliance.entity.AuditEngagement;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.service.AuditEngagementService;
import com.tss.grc.module.compliance.service.CapaWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Endpoints for buyer-assessment §5.3 recommendations:
 *  · /audit-engagements         — recommendation #3 (external audit lifecycle)
 *  · /findings/{id}/capa/...    — recommendation #6 (CAPA closure verification)
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance — Audit Engagements & CAPA Workflow")
@SecurityRequirement(name = "bearerAuth")
public class AuditEngagementController {

    private final AuditEngagementService engagementService;
    private final CapaWorkflowService capaService;

    // ── Audit Engagements ───────────────────────────────────────────────────

    @GetMapping("/audit-engagements")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Page<AuditEngagement> listEngagements(
            @RequestParam(required = false) String auditType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String framework,
            @PageableDefault(size = 20) Pageable pageable) {
        return engagementService.list(auditType, status, framework, pageable);
    }

    @GetMapping("/audit-engagements/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public AuditEngagement getEngagement(@PathVariable Long id) {
        return engagementService.get(id);
    }

    @PostMapping("/audit-engagements")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public AuditEngagement createEngagement(@RequestBody AuditEngagement req) {
        return engagementService.create(req);
    }

    @PutMapping("/audit-engagements/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public AuditEngagement updateEngagement(@PathVariable Long id, @RequestBody AuditEngagement req) {
        return engagementService.update(id, req);
    }

    @DeleteMapping("/audit-engagements/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEngagement(@PathVariable Long id) {
        engagementService.delete(id);
    }

    @PostMapping("/audit-engagements/{id}/advance")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @Operation(summary = "Advance an engagement to a target lifecycle status")
    public AuditEngagement advanceEngagement(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String target = body.get("status") != null ? body.get("status").toString() : null;
        return engagementService.advance(id, target);
    }

    @PostMapping("/audit-engagements/{id}/schedule-followup")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public AuditEngagement scheduleFollowUp(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object f = body.get("followupDate");
        LocalDate date = f != null ? LocalDate.parse(f.toString()) : null;
        return engagementService.scheduleFollowUp(id, date);
    }

    @GetMapping("/audit-engagements/stats")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Map<String, Object> engagementStats() {
        return engagementService.stats();
    }

    // ── CAPA closure workflow on findings ────────────────────────────────────

    @PostMapping("/findings/{id}/capa/start-implementing")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public AuditFinding startImplementing(@PathVariable Long id) {
        return capaService.markImplementing(id);
    }

    @PostMapping("/findings/{id}/capa/owner-test")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public AuditFinding ownerTest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return capaService.ownerTest(id, body);
    }

    @PostMapping("/findings/{id}/capa/independent-test")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public AuditFinding independentTest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return capaService.independentTest(id, body);
    }

    @PostMapping("/findings/{id}/capa/audit-signoff")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public AuditFinding auditSignoff(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return capaService.auditSignoff(id, body);
    }

    @PostMapping("/findings/{id}/capa/close")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public AuditFinding closeFinding(@PathVariable Long id) {
        return capaService.close(id);
    }
}
