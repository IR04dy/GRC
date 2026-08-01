package com.tss.grc.module.privacy.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.privacy.entity.DataBreach;
import com.tss.grc.module.privacy.entity.DataSubjectRequest;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.privacy.service.PrivacyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/privacy")
@RequiredArgsConstructor
@RequireModule("PDPL")
@Tag(name = "Privacy")
@SecurityRequirement(name = "bearerAuth")
public class PrivacyController {

    private final PrivacyService privacyService;

    // ── Data Breaches ─────────────────────────────────────────────────────────

    @GetMapping("/breaches")
    @PreAuthorize("hasPermission('PRIVACY', 'READ')")
    public Page<DataBreach> listBreaches(@PageableDefault(size = 20) Pageable pageable) {
        return privacyService.listBreaches(pageable);
    }

    @GetMapping("/breaches/{id}")
    @PreAuthorize("hasPermission('PRIVACY', 'READ')")
    public DataBreach getBreach(@PathVariable Long id) {
        return privacyService.getBreach(id);
    }

    @PostMapping("/breaches")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public DataBreach createBreach(@RequestBody DataBreach req) {
        return privacyService.createBreach(req);
    }

    @PutMapping("/breaches/{id}")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    public DataBreach updateBreach(@PathVariable Long id, @RequestBody DataBreach req) {
        return privacyService.updateBreach(id, req);
    }

    @DeleteMapping("/breaches/{id}")
    @PreAuthorize("hasPermission('PRIVACY', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBreach(@PathVariable Long id) {
        privacyService.deleteBreach(id);
    }

    // ── SDAIA Submission Workflow (Open Issue #006) ───────────────────────────
    //
    // PDPL Article 31 — 72-hour SDAIA notification workflow. The DPO submits
    // through the SDAIA portal manually (no machine-readable API), then records
    // the outcome here. See docs/operations/sdaia-breach-notification-workflow.md.

    @GetMapping("/breaches/{id}/sdaia-submission")
    @PreAuthorize("hasPermission('PRIVACY', 'READ')")
    public Map<String, Object> getSdaiaStatus(@PathVariable Long id) {
        return privacyService.getSdaiaStatus(id);
    }

    @PostMapping("/breaches/{id}/sdaia-submission")
    @PreAuthorize("hasAnyRole('DPO','CISO','PLATFORM_ADMIN')")
    public DataBreach recordSdaiaSubmission(@PathVariable Long id,
                                            @RequestBody SdaiaSubmissionRequest body) {
        return privacyService.recordSdaiaSubmission(
            id,
            body.submissionRef(),
            body.submittedAt(),
            body.status());
    }

    /**
     * Payload for {@code POST /api/v1/privacy/breaches/{id}/sdaia-submission}.
     *
     * @param status         target state: SUBMITTED (default) | IN_PREPARATION | ACKNOWLEDGED
     * @param submissionRef  SDAIA portal reference number from the acknowledgement
     * @param submittedAt    when the submission was posted (null = stamp now)
     */
    public record SdaiaSubmissionRequest(
        String status,
        String submissionRef,
        java.time.LocalDateTime submittedAt
    ) {}

    // ── DSR ───────────────────────────────────────────────────────────────────

    @GetMapping("/dsrs")
    @PreAuthorize("hasPermission('PRIVACY', 'READ')")
    public Page<DataSubjectRequest> listDsrs(@PageableDefault(size = 20) Pageable pageable) {
        return privacyService.listDsrs(pageable);
    }

    @GetMapping("/dsrs/{id}")
    @PreAuthorize("hasPermission('PRIVACY', 'READ')")
    public DataSubjectRequest getDsr(@PathVariable Long id) {
        return privacyService.getDsr(id);
    }

    @PostMapping("/dsrs")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public DataSubjectRequest createDsr(@RequestBody DataSubjectRequest req) {
        return privacyService.createDsr(req);
    }

    @PutMapping("/dsrs/{id}")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    public DataSubjectRequest updateDsr(@PathVariable Long id, @RequestBody DataSubjectRequest req) {
        return privacyService.updateDsr(id, req);
    }

    @DeleteMapping("/dsrs/{id}")
    @PreAuthorize("hasPermission('PRIVACY', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDsr(@PathVariable Long id) {
        privacyService.deleteDsr(id);
    }

    // ── ROPA ──────────────────────────────────────────────────────────────────

    @GetMapping("/ropa")
    @PreAuthorize("hasPermission('PRIVACY', 'READ')")
    public Page<ProcessingActivity> listRopa(@PageableDefault(size = 20) Pageable pageable) {
        return privacyService.listRopa(pageable);
    }

    @GetMapping("/ropa/{id}")
    @PreAuthorize("hasPermission('PRIVACY', 'READ')")
    public ProcessingActivity getRopa(@PathVariable Long id) {
        return privacyService.getRopa(id);
    }

    @PostMapping("/ropa")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessingActivity createRopa(@RequestBody ProcessingActivity req) {
        return privacyService.createRopa(req);
    }

    @PutMapping("/ropa/{id}")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    public ProcessingActivity updateRopa(@PathVariable Long id, @RequestBody ProcessingActivity req) {
        return privacyService.updateRopa(id, req);
    }

    @DeleteMapping("/ropa/{id}")
    @PreAuthorize("hasPermission('PRIVACY', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRopa(@PathVariable Long id) {
        privacyService.deleteRopa(id);
    }

    // ── Breach workflow (PDPL Article 20) ────────────────────────────────────

    @PostMapping("/breaches/{id}/contain")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    public DataBreach containBreach(@PathVariable Long id) {
        return privacyService.containBreach(id);
    }

    @PostMapping("/breaches/{id}/notify-data-office")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    public DataBreach notifyDataOffice(@PathVariable Long id) {
        return privacyService.notifyDataOffice(id);
    }

    @PostMapping("/breaches/{id}/close")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    public DataBreach closeBreach(@PathVariable Long id) {
        return privacyService.closeBreach(id);
    }

    // ── DSR workflow (PDPL 30-day fulfilment SLA) ─────────────────────────────

    @PostMapping("/dsrs/{id}/start")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    public DataSubjectRequest startDsr(@PathVariable Long id) {
        return privacyService.startDsr(id);
    }

    @PostMapping("/dsrs/{id}/fulfil")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    public DataSubjectRequest fulfilDsr(@PathVariable Long id) {
        return privacyService.fulfilDsr(id);
    }

    @PostMapping("/dsrs/{id}/extend")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    public DataSubjectRequest extendDsr(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer days = body.get("days") instanceof Number n ? n.intValue() : null;
        return privacyService.extendDsr(id, days);
    }

    @PostMapping("/dsrs/{id}/reject")
    @PreAuthorize("hasPermission('PRIVACY', 'WRITE')")
    public DataSubjectRequest rejectDsr(@PathVariable Long id) {
        return privacyService.rejectDsr(id);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('PRIVACY', 'READ')")
    public Map<String, Long> getStats() {
        return privacyService.getStats();
    }
}
