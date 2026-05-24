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
 * <p>Read guards are broad ({@code isAuthenticated}) because every
 * user benefits from in-page guidance regardless of role. Write
 * guards are narrow ({@code PLATFORM_ADMIN}, {@code CONTENT_EDITOR},
 * {@code COMPLIANCE_OFFICER}) — help content carries regulator
 * anchors so authoring is a regulated activity.
 */
@RestController
@RequestMapping("/api/v1/platform/help")
@RequiredArgsConstructor
@Tag(name = "Contextual Help", description = "Embedded contextual help — Sprint 12")
@SecurityRequirement(name = "bearerAuth")
public class HelpController {

    private static final String READ_GUARD  = "isAuthenticated()";
    private static final String LIST_GUARD  = "hasAnyRole('PLATFORM_ADMIN','CONTENT_EDITOR','COMPLIANCE_OFFICER')";
    private static final String WRITE_GUARD = "hasAnyRole('PLATFORM_ADMIN','CONTENT_EDITOR')";

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
    @PreAuthorize(LIST_GUARD)
    @Operation(summary = "Admin — list every help topic visible to the tenant.")
    public List<HelpTopicResponse> listAll() {
        return service.listAll();
    }

    @GetMapping("/topics/{externalId}")
    @PreAuthorize(LIST_GUARD)
    @Operation(summary = "Admin — fetch a single topic by external id.")
    public HelpTopicResponse get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @PostMapping("/topics")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Admin — create a new tenant-scoped help topic.")
    public ResponseEntity<HelpTopicResponse> create(@Valid @RequestBody HelpTopicRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/topics/{externalId}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Admin — update an existing help topic.")
    public HelpTopicResponse update(@PathVariable UUID externalId,
                                    @Valid @RequestBody HelpTopicRequest req) {
        return service.update(externalId, req);
    }

    @DeleteMapping("/topics/{externalId}")
    @PreAuthorize(WRITE_GUARD)
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
