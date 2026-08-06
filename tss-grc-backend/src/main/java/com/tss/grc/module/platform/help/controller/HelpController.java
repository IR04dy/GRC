package com.tss.grc.module.platform.help.controller;

import com.tss.grc.module.platform.help.dto.HelpDtos.HelpLookupResponse;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpTopicRequest;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpTopicResponse;
import com.tss.grc.module.platform.help.service.HelpTopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for contextual help — Sprint 12.
 *
 * <p>Read guards are broad ({@code isAuthenticated}) because every user benefits from in-page
 * guidance regardless of role. Authoring is narrow, because help content carries regulator anchors.
 *
 * <h3>F2-52 — the authoring guard named a role that does not exist</h3>
 * This javadoc used to say the write guards were {@code PLATFORM_ADMIN}, {@code CONTENT_EDITOR} and
 * {@code COMPLIANCE_OFFICER}. <b>Two thirds of that was false.</b> {@code CONTENT_EDITOR} matches no
 * row in {@code iam.roles} in either database, so {@code hasAnyRole('PLATFORM_ADMIN','CONTENT_EDITOR')}
 * was a permanently false OR leg beside a real one — and a role guard never reaches
 * {@code ModulePermissionEvaluator}, so it never reaches the PLATFORM_ADMIN bypass either. Authoring
 * every help topic was <b>the platform administrator's job alone, by attrition rather than by
 * decision</b>. And {@code COMPLIANCE_OFFICER} was never on the write guard at all; it was on the
 * <em>list</em> guard, so measured live before this change:
 *
 * <pre>
 *   GRC_ADMINISTRATOR   GET /topics 403   POST /topics 403
 *   COMPLIANCE_OFFICER  GET /topics 200   POST /topics 403
 * </pre>
 *
 * A compliance officer could read the entire authoring inventory and author nothing in it — while
 * this comment told the next reader they were an author.
 *
 * <h3>Why {@code KB:WRITE} and not a new permission</h3>
 * {@code KbArticleController} — the platform's other content-authoring surface — already guards with
 * {@code hasPermission('KB','WRITE')}, catalogued by V329 and granted to {@code GRC_ADMINISTRATOR},
 * {@code ORG_ADMIN} and {@code COMPLIANCE_OFFICER}: the two tenant administrators plus the persona
 * accountable for compliance content. Help topics are the same class of artefact as KB articles —
 * regulator-anchored guidance — so they take the same permission rather than a second one. No
 * migration: the permission and all three grants already exist in dev and prod.
 *
 * <p>⚠ <b>{@code KB:READ} deliberately does not exist</b> (ADR-017 D-7, as amended). Reads here stay
 * {@code isAuthenticated()} for the same reason they do on KB articles: a grant list can only ever
 * wrongly exclude someone from guidance they need. A later uniformity sweep should not "fix" that.
 *
 * <p>⚠ The admin inventory ({@code GET /topics}) shares the authoring guard rather than getting a
 * weaker one. Listing every topic including unpublished drafts <em>is</em> part of authoring, and
 * inventing a read-only reviewer tier would mean inventing its population too.
 */
@RestController
@RequestMapping("/api/v1/platform/help")
@RequiredArgsConstructor
@Tag(name = "Contextual Help", description = "Embedded contextual help — Sprint 12")
@SecurityRequirement(name = "bearerAuth")
public class HelpController {

    /** Every authenticated user, deliberately — see the class javadoc and ADR-017 D-7. */
    private static final String READ_GUARD = "isAuthenticated()";

    /**
     * The authoring surface: the admin inventory and every mutation. F2-52 — was
     * {@code hasAnyRole('PLATFORM_ADMIN','CONTENT_EDITOR')} for writes and the same list plus
     * {@code COMPLIANCE_OFFICER} for the inventory.
     */
    private static final String AUTHOR_GUARD = "hasPermission('KB','WRITE')";

    /**
     * Deletion stays with the platform administrator, matching {@code KbArticleController#delete}.
     * Behaviour-preserving: the old guard already reduced to PLATFORM_ADMIN alone. Removing a help
     * topic destroys the only copy of guidance an operator may be mid-task with, so it is the one
     * action here that is not simply authoring.
     */
    private static final String DELETE_GUARD = "hasRole('PLATFORM_ADMIN')";

    private final HelpTopicService service;

    // ─── Drawer lookup + search ───────────────────────────────────

    @GetMapping("/lookup")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Resolve help topics for the current URL route.")
    public HelpLookupResponse lookup(@RequestParam("route") String route) {
        return service.lookupForRoute(route);
    }

    @GetMapping("/search")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Search help topics by free-text query.")
    public List<HelpTopicResponse> search(@RequestParam("q") String q) {
        return service.searchTopics(q);
    }

    @GetMapping("/module/{module}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List help topics for a given module.")
    public List<HelpTopicResponse> listForModule(@PathVariable String module) {
        return service.listForModule(module);
    }

    // ─── Admin surface ────────────────────────────────────────────

    @GetMapping("/topics")
    @PreAuthorize(AUTHOR_GUARD)
    @Operation(summary = "Admin — list every help topic visible to the tenant.")
    public List<HelpTopicResponse> listAll() {
        return service.listAll();
    }

    @GetMapping("/topics/{externalId}")
    @PreAuthorize(AUTHOR_GUARD)
    @Operation(summary = "Admin — fetch a single topic by external id.")
    public HelpTopicResponse get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @PostMapping("/topics")
    @PreAuthorize(AUTHOR_GUARD)
    @Operation(summary = "Admin — create a new tenant-scoped help topic.")
    public ResponseEntity<HelpTopicResponse> create(@Valid @RequestBody HelpTopicRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/topics/{externalId}")
    @PreAuthorize(AUTHOR_GUARD)
    @Operation(summary = "Admin — update an existing help topic.")
    public HelpTopicResponse update(@PathVariable UUID externalId,
                                    @Valid @RequestBody HelpTopicRequest req) {
        return service.update(externalId, req);
    }

    @DeleteMapping("/topics/{externalId}")
    @PreAuthorize(DELETE_GUARD)
    @Operation(summary = "Admin — soft-delete a help topic.")
    public ResponseEntity<Void> delete(@PathVariable UUID externalId) {
        service.delete(externalId);
        return ResponseEntity.noContent().build();
    }

    // ─── Analytics — view tracking ────────────────────────────────

    @PostMapping("/topics/{externalId}/track-view")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Fire-and-forget analytics — record that this topic was opened.")
    public ResponseEntity<Void> trackView(@PathVariable UUID externalId,
                                          @RequestParam(value = "route", required = false) String route) {
        service.trackView(externalId, route);
        return ResponseEntity.accepted().build();
    }
}
