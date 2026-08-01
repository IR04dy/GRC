package com.tss.grc.module.privacy.processor.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.privacy.processor.entity.ProcessorAssessment;
import com.tss.grc.module.privacy.processor.service.ProcessorAssessmentService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/privacy/processor-assessments")
@RequiredArgsConstructor
@RequireModule("PDPL")
@Tag(name = "Processor due diligence (PDPL Art. 28)")
public class ProcessorAssessmentController {

    private final ProcessorAssessmentService service;

    @GetMapping
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public Page<ProcessorAssessment> list(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        return status == null ? service.list(p) : service.listByStatus(status, p);
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public ProcessorAssessment get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ResponseEntity<ProcessorAssessment> create(@RequestBody ProcessorAssessment req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PostMapping("/{id}/score")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ProcessorAssessment score(@PathVariable Long id, @RequestBody Map<String, String> controls) {
        // F2-22b(iv): stamp who scored it — assessedBy/assessorName had no setter anywhere.
        return service.scoreAndComplete(id, controls, actorId(), actor());
    }

    /**
     * F2-22b(iv) — the body no longer decides who approved.
     *
     * <p>It used to be {@code body.getOrDefault("approverName", "admin")}, and the SPA passed the
     * literal {@code 'admin'}, so the register's approver column was a constant. Any
     * {@code approverName} still sent is now ignored rather than rejected: the SPA has been
     * updated in the same commit, but an older bundle served from cache would otherwise start
     * failing on a field it has no reason to know is gone.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ProcessorAssessment approve(@PathVariable Long id,
                                       @RequestBody(required = false) Map<String, String> body) {
        return service.approve(id, actor());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ProcessorAssessment reject(@PathVariable Long id) { return service.reject(id); }

    // ── Authenticated actor (same shape as BcmCycleController / CommitteeResolutionController) ──
    private String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    private Long actorId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
}
