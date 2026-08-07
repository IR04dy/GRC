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
 * <p>Authorisation is by permission, not by role name (F2-145(e) slice 5). Reads are a
 * three-step need-to-know gradient — list metadata, then the entries inside a list, then the
 * hit queue — because the hit queue names customers who matched, and front-line onboarding
 * and KYC roles are deliberately excluded from it. Writes split by operation: screening is
 * wide (real-time onboarding), list curation and hit disposition are AML/compliance only, and
 * an upstream refresh is narrower still. See the constants below for the measured
 * per-tier populations, and V370 for why a population diff cannot be used to re-derive them.
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

    // ── Authorisation ────────────────────────────────────────────────────────
    // F2-145(e) slice 5. Converted from fourteen hasAnyRole(...) recitations spanning six DISTINCT
    // name-sets — the most of any module in the queue — onto seven catalogued actions.
    // Populations measured against iam.role_permissions BEFORE the edit, and preserved exactly:
    //
    //   READ           7  AML_OFFICER COMPLIANCE_OFFICER CISO INTERNAL_AUDITOR KYC_OFFICER
    //                     ONBOARDING_AGENT AUDITOR        — list metadata + screening-check records
    //   READ_ENTRIES   6  …minus ONBOARDING_AGENT          — the sanctioned names inside a list
    //   READ_HITS      5  …minus KYC_OFFICER too           — the hit queue + dashboard
    //   WRITE          4  AML_OFFICER COMPLIANCE_OFFICER KYC_OFFICER ONBOARDING_AGENT — run a screening
    //   MANAGE_LISTS   2  AML_OFFICER COMPLIANCE_OFFICER   — create/amend/bulk-import a list
    //   REFRESH_LISTS  1  AML_OFFICER                      — pull entries from upstream
    //   APPROVE        2  AML_OFFICER COMPLIANCE_OFFICER   — clear or escalate a hit
    //
    // ⚠ The three READ tiers are a need-to-know gradient, not verbosity. A single AML:READ would give
    // every onboarding agent the hit queue — which customers matched a sanctions list — and disclosing
    // that is the FATF R.21 / SAMA tipping-off offence. ⚠ Do not re-derive this from a population diff:
    // in dev all three tiers resolve to the SAME three users, because the roles that differentiate them
    // (AML_OFFICER, KYC_OFFICER, ONBOARDING_AGENT) have 0 active users. An empty diff there is the
    // instrument failing to see a distinction, not the absence of one. See V370's header.
    //
    // MANAGE_LISTS and APPROVE have the same population today and are still separate actions: curating
    // the list that produces a hit and disposing of that hit are the two halves of a maker/checker pair,
    // and a tenant must be able to grant one without the other. Same access, more expressible.
    private static final String READ_GUARD         = "hasPermission('AML','READ')";
    private static final String READ_ENTRIES_GUARD = "hasPermission('AML','READ_ENTRIES')";
    private static final String READ_HITS_GUARD    = "hasPermission('AML','READ_HITS')";
    private static final String SCREEN_GUARD       = "hasPermission('AML','WRITE')";
    private static final String MANAGE_LISTS_GUARD = "hasPermission('AML','MANAGE_LISTS')";
    private static final String REFRESH_GUARD      = "hasPermission('AML','REFRESH_LISTS')";
    private static final String DECIDE_HIT_GUARD   = "hasPermission('AML','APPROVE')";

    // ── Lists ────────────────────────────────────────────────────────

    @GetMapping("/lists")
    @PreAuthorize(READ_GUARD)
    public List<ListResponse> listLists() {
        return listService.listVisible();
    }

    @GetMapping("/lists/{id}")
    @PreAuthorize(READ_GUARD)
    public ListResponse getList(@PathVariable Long id) {
        return listService.get(id);
    }

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_LISTS_GUARD)
    public ListResponse createList(@RequestBody @Valid ListCreateRequest req) {
        return listService.create(req);
    }

    @PatchMapping("/lists/{id}")
    @PreAuthorize(MANAGE_LISTS_GUARD)
    public ListResponse updateList(@PathVariable Long id, @RequestBody @Valid ListUpdateRequest req) {
        return listService.update(id, req);
    }

    @PostMapping("/lists/{id}/refresh")
    @PreAuthorize(REFRESH_GUARD)
    public ListResponse refreshList(@PathVariable Long id) {
        return listService.refresh(id);
    }

    @GetMapping("/lists/{id}/entries")
    @PreAuthorize(READ_ENTRIES_GUARD)
    public List<EntryResponse> listEntries(@PathVariable Long id) {
        return listService.listEntries(id);
    }

    @PostMapping("/lists/{id}/entries/bulk-import")
    @PreAuthorize(MANAGE_LISTS_GUARD)
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
    @PreAuthorize(SCREEN_GUARD)
    public CheckResponse performScreening(@RequestBody @Valid ScreenRequest req) {
        return screeningService.screen(req);
    }

    @GetMapping("/checks")
    @PreAuthorize(READ_GUARD)
    public List<CheckResponse> listChecks(@RequestParam(required = false) String result) {
        if (result == null || result.isBlank()) {
            return screeningService.listChecks();
        }
        return screeningService.listChecksByResult(result);
    }

    @GetMapping("/checks/{id}")
    @PreAuthorize(READ_GUARD)
    public CheckResponse getCheck(@PathVariable Long id) {
        return screeningService.getCheck(id);
    }

    // ── Hits ─────────────────────────────────────────────────────────

    @GetMapping("/hits")
    @PreAuthorize(READ_HITS_GUARD)
    public List<HitResponse> listHits(@RequestParam(defaultValue = "PENDING_REVIEW") String status) {
        return hitService.listByStatus(status);
    }

    @PostMapping("/hits/{id}/review")
    @PreAuthorize(DECIDE_HIT_GUARD)
    public HitResponse reviewHit(@PathVariable Long id, @RequestBody @Valid HitReviewRequest req) {
        return hitService.reviewHit(id, req);
    }

    @PostMapping("/hits/{id}/escalate")
    @PreAuthorize(DECIDE_HIT_GUARD)
    public HitResponse escalateHit(@PathVariable Long id, @RequestBody @Valid HitEscalateRequest req) {
        return hitService.escalate(id, req);
    }

    // ── Dashboard ────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize(READ_HITS_GUARD)
    public DashboardResponse dashboard() {
        return dashboardService.dashboard();
    }
}
