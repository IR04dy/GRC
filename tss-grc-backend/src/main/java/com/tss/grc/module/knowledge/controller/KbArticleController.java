package com.tss.grc.module.knowledge.controller;

import com.tss.grc.module.knowledge.dto.KbArticleDto;
import com.tss.grc.module.knowledge.service.KbArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Knowledge Base — read + write endpoints (Sprint 4 §2.9 completion).
 *
 * <pre>
 *   GET    /api/kb/articles                — list published (optional ?category=)
 *   GET    /api/kb/articles/{slug}         — read one + record view
 *   POST   /api/kb/articles                — create draft (admin/author only)
 *   PUT    /api/kb/articles/{slug}         — edit content
 *   POST   /api/kb/articles/{slug}/transition — publish / archive / re-draft
 *   DELETE /api/kb/articles/{slug}         — soft-delete (admin only)
 * </pre>
 */
@RestController
@RequestMapping("/api/kb/articles")
@RequiredArgsConstructor
public class KbArticleController {

    /**
     * F2-53 / F2-52 — was {@code hasRole('PLATFORM_ADMIN') or hasAuthority('KB_WRITE')}.
     *
     * <p>{@code KB_WRITE} is the raw-authority spelling of the permission {@code KB:WRITE} — but
     * {@code KB} was absent from {@code iam.permissions} entirely, so no role could hold it, so that
     * leg was permanently false and the guard reduced to {@code hasRole('PLATFORM_ADMIN')}.
     * <b>Authoring, editing and publishing every knowledge-base article was the platform
     * administrator's job alone</b> — by attrition, not by decision. A ghost <em>permission</em>
     * rather than a ghost role, failing identically and just as silently; the role-guard census's
     * own source comment uses this exact expression as its worked example.
     *
     * <p>{@code V329} catalogues {@code KB:WRITE} and grants it to {@code GRC_ADMINISTRATOR},
     * {@code ORG_ADMIN} and {@code COMPLIANCE_OFFICER} — the two tenant administrators plus the
     * persona actually accountable for the content, which is PDPL/ECC/NDMO/Nazaha/NCEMA guidance.
     * Without that third grant the fix would only move authoring from the platform administrator to
     * the tenant administrator, who is not the author of compliance content either.
     */
    private static final String WRITE_GUARD = "hasPermission('KB','WRITE')";

    private final KbArticleService service;

    // F2-53 / ADR-017 D-7 — reads stay `isAuthenticated()`, deliberately.
    //
    // This is the one guard in the platform that should NOT become a permission, and it is worth
    // saying why so a later uniformity sweep does not "fix" it. Measured by reading the corpus
    // rather than the guards: all 22 articles are PUBLISHED / visibility INTERNAL, and they are
    // staff operational guidance — "Whistleblowing Channel — How to Report", "Disclosing a Conflict
    // of Interest (Nazaha)", "Handling Data-Subject Requests under PDPL", "Business Continuity
    // Activation Steps (NCEMA)", "Delegating approvals when you're away".
    //
    // A whistleblowing procedure readable only by permission-holders is worse than none: every
    // employee must be able to find out how to report, which is the point of Nazaha-aligned
    // disclosure and of this platform's own ethics module. `isAuthenticated()` cannot wrongly
    // exclude an employee; a grant list can only ever wrongly exclude one. ADR-017 D-7 originally
    // proposed granting KB:READ broadly — reading the content showed that granting it at all is
    // worse than the guard it would replace, so V329 catalogues WRITE only and the ADR is amended.
    //
    // ⬜ What IS missing here is not a permission but a filter: `visibility` is stored on every
    // article and settable through the API, and nothing anywhere reads it. Raised as a finding
    // rather than half-built, because deciding what CONFIDENTIAL/PUBLIC mean is a product call.

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<KbArticleDto> listPublished(@RequestParam(required = false) String category) {
        return service.listPublished(category);
    }

    /**
     * F2-53 (found while measuring it) — this used to serve a DRAFT.
     *
     * <p>{@code listPublished} filters {@code status='PUBLISHED'}, but this route resolved by slug
     * alone with <b>no status filter</b>, so any authenticated caller who knew or guessed a slug
     * could read an article that had never been published — an author's in-progress revision of the
     * whistleblowing or conflict-of-interest procedure, visible before anyone reviewed it. The list
     * endpoint hid the defect: a draft never appeared in it, so nothing pointed at the slug.
     * {@code publishedBySlug} now applies the same status filter the list has always applied, and
     * anyone holding {@code KB:WRITE} may still read any status so authors can see their own drafts.
     */
    @GetMapping("/{slug}")
    @PreAuthorize("isAuthenticated()")
    public KbArticleDto bySlug(@PathVariable String slug) {
        // Record a view as a side-effect of read.
        service.recordView(slug);
        return service.bySlug(slug);
    }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    public ResponseEntity<KbArticleDto> create(@RequestBody @Valid KbArticleDto req,
                                               Authentication auth) {
        return ResponseEntity.ok(service.create(req, auth.getName()));
    }

    @PutMapping("/{slug}")
    @PreAuthorize(WRITE_GUARD)
    public KbArticleDto update(@PathVariable String slug, @RequestBody @Valid KbArticleDto req) {
        return service.update(slug, req);
    }

    @PostMapping("/{slug}/transition")
    @PreAuthorize(WRITE_GUARD)
    public KbArticleDto transition(@PathVariable String slug,
                                   @RequestBody @Valid KbArticleDto.TransitionRequest req) {
        return service.transition(slug, req);
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        service.softDelete(slug);
        return ResponseEntity.noContent().build();
    }
}
