package com.tss.grc.module.platform.savedviews.controller;

import com.tss.grc.module.platform.savedviews.dto.SavedViewDto;
import com.tss.grc.module.platform.savedviews.service.SavedViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for per-user saved views.
 *
 * <p>Authentication is enough — these rows are personal preference,
 * not module-scoped data. We deliberately don't gate on a module
 * permission because the same user might have views across modules
 * (RoPA in compliance, vulnerability triage in cyber, etc.) and
 * applying module checks would force us to know the page→module
 * mapping in the backend, which is a frontend concern.
 */
@RestController
@RequestMapping("/api/platform/saved-views")
@RequiredArgsConstructor
@Tag(name = "Saved Views", description = "Per-user saved combinations of filter + sort + visible columns")
public class SavedViewController {

    private final SavedViewService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List my saved views for a page")
    public List<SavedViewDto> list(@RequestParam("pageKey") String pageKey) {
        return service.listForPage(pageKey);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SavedViewDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new saved view")
    public ResponseEntity<SavedViewDto> create(@Valid @RequestBody SavedViewDto in) {
        return ResponseEntity.ok(service.create(in));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Replace an existing saved view")
    public SavedViewDto update(@PathVariable Long id, @Valid @RequestBody SavedViewDto in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Soft-delete a saved view")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
