package com.tss.grc.module.compliance.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.entity.Policy;
import com.tss.grc.module.compliance.entity.RegulatoryChange;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.compliance.service.PolicyService;
import com.tss.grc.module.compliance.service.RegulatoryChangeService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints for the buyer-assessment §5.3 critical recommendations:
 *  · /regulatory-changes — recommendation #1 (regulatory change management)
 *  · /policies           — recommendation #2 (policy lifecycle)
 *
 * The existing ControlController owns /controls, /assessments, /findings — this
 * controller adds the two new resource trees alongside without disturbing them.
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@RequireModule("COMPLIANCE")
@Tag(name = "Compliance — Regulatory Change & Policy")
@SecurityRequirement(name = "bearerAuth")
public class RegChangePolicyController {

    private final RegulatoryChangeService changeService;
    private final PolicyService policyService;
    private final AuditFindingRepository findingRepo;

    // ── Regulatory Changes ──────────────────────────────────────────────────

    @GetMapping("/regulatory-changes")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Page<RegulatoryChange> listChanges(@PageableDefault(size = 20) Pageable pageable) {
        return changeService.list(pageable);
    }

    @GetMapping("/regulatory-changes/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public RegulatoryChange getChange(@PathVariable Long id) {
        return changeService.get(id);
    }

    @PostMapping("/regulatory-changes")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public RegulatoryChange createChange(@RequestBody RegulatoryChange req) {
        return changeService.create(req);
    }

    @PutMapping("/regulatory-changes/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public RegulatoryChange updateChange(@PathVariable Long id, @RequestBody RegulatoryChange req) {
        return changeService.update(id, req);
    }

    @DeleteMapping("/regulatory-changes/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChange(@PathVariable Long id) {
        changeService.delete(id);
    }

    @PostMapping("/regulatory-changes/{id}/assess-applicability")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public RegulatoryChange assessApplicability(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return changeService.assessApplicability(id, body);
    }

    @PostMapping("/regulatory-changes/{id}/assess-impact")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public RegulatoryChange assessImpact(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return changeService.assessImpact(id, body);
    }

    @PostMapping("/regulatory-changes/{id}/mark-controls-updated")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public RegulatoryChange markControlsUpdated(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return changeService.markControlsUpdated(id, body);
    }

    @PostMapping("/regulatory-changes/{id}/close")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public RegulatoryChange closeChange(@PathVariable Long id) {
        return changeService.close(id);
    }

    @GetMapping("/regulatory-changes/stats")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Map<String, Long> changeStats() {
        return changeService.stats();
    }

    // ── Policies ────────────────────────────────────────────────────────────

    @GetMapping("/policies")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Page<Policy> listPolicies(@PageableDefault(size = 20) Pageable pageable) {
        return policyService.list(pageable);
    }

    @GetMapping("/policies/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Policy getPolicy(@PathVariable Long id) {
        return policyService.get(id);
    }

    // ── Legacy policy writes RETIRED (CH-6 / PM-C1) ─────────────────────────
    // The write lifecycle for the legacy compliance.policies register is retired:
    // policy authoring lives solely on the canonical register
    // (POST/PUT/DELETE /api/policy/documents + /transition). Only the read
    // endpoints (list/get/stats) remain, still serving the /policies/:id detail
    // page until it is repointed to the canonical API in CH-9. PolicyService keeps
    // the now HTTP-unexposed lifecycle methods (+ their tests) through this
    // expand-contract window; the whole legacy service goes with CH-9.

    @GetMapping("/policies/stats")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Map<String, Long> policyStats() {
        return policyService.stats();
    }

    // ── Findings aging engine — assessment §5.3 recommendation #4 ────────────
    //
    // Tier bands by days-open:
    //   0–29   ON_TRACK
    //   30–59  MANAGER_ESCALATED
    //   60–89  DIRECTOR_ESCALATED
    //   ≥ 90   EXEC_ESCALATED
    //
    // Returns the open findings only (status != CLOSED), enriched with
    // daysOpen and escalationTier so the frontend doesn't need to compute it.

    @GetMapping("/findings/aging")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "Open findings enriched with daysOpen and escalationTier — assessment §5.3 recommendation #4")
    public List<Map<String, Object>> findingsAging() {
        Long org = TenantContext.getCurrentTenant();
        Page<AuditFinding> all = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000));
        LocalDate today = LocalDate.now();
        return all.getContent().stream()
            .filter(f -> !"CLOSED".equals(f.getStatus()))
            .map(f -> {
                LocalDateTime created = f.getCreatedAt();
                long daysOpen = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
                String tier = daysOpen >= 90 ? "EXEC_ESCALATED"
                            : daysOpen >= 60 ? "DIRECTOR_ESCALATED"
                            : daysOpen >= 30 ? "MANAGER_ESCALATED"
                            : "ON_TRACK";
                Map<String, Object> out = new HashMap<>();
                out.put("id",              f.getId());
                out.put("title",           f.getTitle());
                out.put("severity",        f.getSeverity());
                out.put("owner",           f.getOwner());
                out.put("dueDate",         f.getDueDate());
                out.put("status",          f.getStatus());
                out.put("createdAt",       f.getCreatedAt());
                out.put("daysOpen",        daysOpen);
                out.put("escalationTier",  tier);
                out.put("capaStage",       f.getCapaStage());
                return out;
            })
            .toList();
    }

    @GetMapping("/findings/aging/stats")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "Aged-findings counts by escalation tier for board pack feed")
    public Map<String, Long> findingsAgingStats() {
        Long org = TenantContext.getCurrentTenant();
        Page<AuditFinding> all = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000));
        LocalDate today = LocalDate.now();
        Map<String, Long> counts = new HashMap<>();
        counts.put("onTrack",            0L);
        counts.put("managerEscalated",   0L);
        counts.put("directorEscalated",  0L);
        counts.put("execEscalated",      0L);
        for (AuditFinding f : all.getContent()) {
            if ("CLOSED".equals(f.getStatus())) continue;
            LocalDateTime created = f.getCreatedAt();
            long daysOpen = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
            String key = daysOpen >= 90 ? "execEscalated"
                       : daysOpen >= 60 ? "directorEscalated"
                       : daysOpen >= 30 ? "managerEscalated"
                       : "onTrack";
            counts.merge(key, 1L, Long::sum);
        }
        return counts;
    }
}
