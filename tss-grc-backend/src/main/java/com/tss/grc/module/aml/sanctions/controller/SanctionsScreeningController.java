package com.tss.grc.module.aml.sanctions.controller;

import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.CheckResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.DashboardResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryBulkImportRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryBulkImportResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitEscalateRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitReviewRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ListCreateRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ListResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ListUpdateRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ScreenRequest;
import com.tss.grc.module.aml.sanctions.service.SanctionsDashboardService;
import com.tss.grc.module.aml.sanctions.service.SanctionsHitService;
import com.tss.grc.module.aml.sanctions.service.SanctionsListService;
import com.tss.grc.module.aml.sanctions.service.SanctionsScreeningService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sprint 13 — REST surface for Sanctions Screening.
 *
 * <pre>
 *   Lists:
 *     GET    /api/v1/aml/sanctions/lists
 *     GET    /api/v1/aml/sanctions/lists/{id}
 *     POST   /api/v1/aml/sanctions/lists
 *     PATCH  /api/v1/aml/sanctions/lists/{id}
 *     POST   /api/v1/aml/sanctions/lists/{id}/refresh
 *     GET    /api/v1/aml/sanctions/lists/{id}/entries
 *     POST   /api/v1/aml/sanctions/lists/{id}/entries/bulk-import
 *
 *   Checks:
 *     POST   /api/v1/aml/sanctions/checks            — perform screening
 *     GET    /api/v1/aml/sanctions/checks
 *     GET    /api/v1/aml/sanctions/checks/{id}
 *     GET    /api/v1/aml/sanctions/checks?result=…
 *
 *   Hits:
 *     GET    /api/v1/aml/sanctions/hits?status=PENDING_REVIEW
 *     POST   /api/v1/aml/sanctions/hits/{id}/review
 *     POST   /api/v1/aml/sanctions/hits/{id}/escalate
 *
 *   Dashboard:
 *     GET    /api/v1/aml/sanctions/dashboard
 * </pre>
 *
 * <p>Read roles are broad (AML/compliance + audit). Write roles tighten
 * by operation — screening is wide (real-time onboarding) but list
 * management and hit triage are restricted to AML/compliance officers.
 */
@RestController
@RequestMapping("/api/v1/aml/sanctions")
@RequiredArgsConstructor
@RequireModule("ETHICS")
@Tag(name = "AML — Sanctions Screening (Sprint 13)")
@SecurityRequirement(name = "bearerAuth")
public class SanctionsScreeningController {

    private final SanctionsListService listService;
    private final SanctionsScreeningService screeningService;
    private final SanctionsHitService hitService;
    private final SanctionsDashboardService dashboardService;

    // ── Lists ────────────────────────────────────────────────────────

    @GetMapping("/lists")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER','CISO',"
        + "'INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','KYC_OFFICER','ONBOARDING_AGENT','AUDITOR')")
    public List<ListResponse> listLists() {
        return listService.listVisible();
    }

    @GetMapping("/lists/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER','CISO',"
        + "'INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','KYC_OFFICER','ONBOARDING_AGENT','AUDITOR')")
    public ListResponse getList(@PathVariable Long id) {
        return listService.get(id);
    }

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER')")
    public ListResponse createList(@RequestBody @Valid ListCreateRequest req) {
        return listService.create(req);
    }

    @PatchMapping("/lists/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER')")
    public ListResponse updateList(@PathVariable Long id, @RequestBody @Valid ListUpdateRequest req) {
        return listService.update(id, req);
    }

    @PostMapping("/lists/{id}/refresh")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER')")
    public ListResponse refreshList(@PathVariable Long id) {
        return listService.refresh(id);
    }

    @GetMapping("/lists/{id}/entries")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER','CISO',"
        + "'INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','KYC_OFFICER','AUDITOR')")
    public List<EntryResponse> listEntries(@PathVariable Long id) {
        return listService.listEntries(id);
    }

    @PostMapping("/lists/{id}/entries/bulk-import")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER')")
    public EntryBulkImportResponse bulkImportEntries(@PathVariable Long id,
                                                     @RequestBody @Valid EntryBulkImportRequest req) {
        // Path id takes precedence over body listId — mismatch is a bad request.
        if (!id.equals(req.listId())) {
            throw new IllegalArgumentException(
                "Path listId (" + id + ") and body listId (" + req.listId() + ") mismatch");
        }
        return listService.bulkImportEntries(req);
    }

    // ── Checks ───────────────────────────────────────────────────────

    @PostMapping("/checks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','KYC_OFFICER','ONBOARDING_AGENT','COMPLIANCE_OFFICER')")
    public CheckResponse performScreening(@RequestBody @Valid ScreenRequest req) {
        return screeningService.screen(req);
    }

    @GetMapping("/checks")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER','CISO',"
        + "'INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','KYC_OFFICER','ONBOARDING_AGENT','AUDITOR')")
    public List<CheckResponse> listChecks(@RequestParam(required = false) String result) {
        if (result == null || result.isBlank()) {
            return screeningService.listChecks();
        }
        return screeningService.listChecksByResult(result);
    }

    @GetMapping("/checks/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER','CISO',"
        + "'INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','KYC_OFFICER','ONBOARDING_AGENT','AUDITOR')")
    public CheckResponse getCheck(@PathVariable Long id) {
        return screeningService.getCheck(id);
    }

    // ── Hits ─────────────────────────────────────────────────────────

    @GetMapping("/hits")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER','CISO',"
        + "'INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','AUDITOR')")
    public List<HitResponse> listHits(@RequestParam(defaultValue = "PENDING_REVIEW") String status) {
        return hitService.listByStatus(status);
    }

    @PostMapping("/hits/{id}/review")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER')")
    public HitResponse reviewHit(@PathVariable Long id, @RequestBody @Valid HitReviewRequest req) {
        return hitService.reviewHit(id, req);
    }

    @PostMapping("/hits/{id}/escalate")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER')")
    public HitResponse escalateHit(@PathVariable Long id, @RequestBody @Valid HitEscalateRequest req) {
        return hitService.escalate(id, req);
    }

    // ── Dashboard ────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','AML_OFFICER','COMPLIANCE_OFFICER','CISO',"
        + "'INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER','AUDITOR')")
    public DashboardResponse dashboard() {
        return dashboardService.dashboard();
    }
}
